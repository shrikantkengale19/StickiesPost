package com.example.stickiespost

import android.os.Bundle
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.room.Room
import com.example.stickiespost.data.local.NotesDatabase
import com.example.stickiespost.data.remote.GeminiCommandInterpreter
import com.example.stickiespost.data.remote.SupabaseClientFactory
import com.example.stickiespost.data.remote.SupabaseNoteSync
import com.example.stickiespost.data.remote.FirebaseNoteSync
import com.example.stickiespost.data.remote.FirebaseNoteStore
import com.example.stickiespost.data.repository.RoomRecurringTaskRepository
import com.example.stickiespost.data.repository.RoomNotesRepository
import com.example.stickiespost.data.security.ApiKeyStore
import com.example.stickiespost.domain.usecase.AddNoteUseCase
import com.example.stickiespost.domain.usecase.AddRecurringTaskUseCase
import com.example.stickiespost.domain.usecase.ProcessCommandUseCase
import com.example.stickiespost.presentation.NotesScreen
import com.example.stickiespost.presentation.FirebaseAuthScreen
import com.example.stickiespost.presentation.NotesViewModel
import com.example.stickiespost.presentation.TasksScreen
import com.example.stickiespost.presentation.TasksViewModel
import com.example.stickiespost.reminders.AndroidReminderScheduler
import com.example.stickiespost.reminders.AndroidTaskReminderScheduler
import com.example.stickiespost.ui.theme.StickiesPostTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val database by lazy {
        Room.databaseBuilder(applicationContext, NotesDatabase::class.java, DATABASE_NAME)
            .addMigrations(
                NotesDatabase.MIGRATION_1_2,
                NotesDatabase.MIGRATION_2_3,
                NotesDatabase.MIGRATION_3_4,
                NotesDatabase.MIGRATION_4_5
            )
            .build()
    }
    private val apiKeyStore by lazy { ApiKeyStore(applicationContext) }
    private val firebaseNoteStore by lazy {
        if (BuildConfig.NOTES_SYNC_PROVIDER == SYNC_PROVIDER_FIREBASE) {
            (application as StickiesPostApplication).firebaseNoteStore
        } else {
            null
        }
    }
    private val notesRepository by lazy {
        RoomNotesRepository(database.noteDao(), firebaseNoteStore)
    }
    private val taskRepository by lazy { RoomRecurringTaskRepository(database.recurringTaskDao()) }
    private val taskReminderScheduler by lazy { AndroidTaskReminderScheduler(applicationContext) }
    private val notesViewModel by viewModels<NotesViewModel> {
        NotesViewModel.factory(
            processCommand = ProcessCommandUseCase(
                interpreter = GeminiCommandInterpreter(apiKeyStore),
                repository = notesRepository,
                reminderScheduler = AndroidReminderScheduler(applicationContext)
            ),
            repository = notesRepository,
            apiKeyStore = apiKeyStore,
            addNoteUseCase = AddNoteUseCase(notesRepository),
            reminderScheduler = AndroidReminderScheduler(applicationContext)
        )
    }
    private val tasksViewModel by viewModels<TasksViewModel> {
        TasksViewModel.factory(
            repository = taskRepository,
            addTaskUseCase = AddRecurringTaskUseCase(taskRepository),
            reminderScheduler = taskReminderScheduler
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        when (BuildConfig.NOTES_SYNC_PROVIDER) {
            SYNC_PROVIDER_SUPABASE -> startSupabaseSync()
            SYNC_PROVIDER_FIREBASE -> {
                startFirebaseTestSignIn()
                startFirebaseSync()
            }
            else -> error("Unsupported notes sync provider: ${BuildConfig.NOTES_SYNC_PROVIDER}")
        }
        setContent {
            StickiesPostTheme {
                if (BuildConfig.NOTES_SYNC_PROVIDER == SYNC_PROVIDER_FIREBASE) {
                    val store = checkNotNull(firebaseNoteStore)
                    val user by store.user.collectAsState(initial = store.currentUser)
                    if (user == null) {
                        FirebaseAuthScreen(store)
                    } else {
                        NotesAppContent(
                            notesViewModel = notesViewModel,
                            tasksViewModel = tasksViewModel,
                            currentUserEmail = user?.email,
                            onSignOut = store::signOut
                        )
                    }
                } else {
                    NotesAppContent(
                        notesViewModel = notesViewModel,
                        tasksViewModel = tasksViewModel,
                        onSignOut = null
                    )
                }
            }
        }
    }

    private fun startSupabaseSync() {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_PUBLISHABLE_KEY.isBlank()) {
            Log.w(TAG, "Supabase sync is disabled; configure supabase.url and supabase.publishable_key in local.properties.")
            return
        }

        lifecycleScope.launch {
            try {
                SupabaseNoteSync(
                    supabase = SupabaseClientFactory.create(
                        projectUrl = BuildConfig.SUPABASE_URL,
                        publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
                    ),
                    noteDao = database.noteDao()
                ).run()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Supabase note sync failed.", exception)
                notesViewModel.showError(
                    exception.localizedMessage ?: "Could not sync notes from Supabase."
                )
            }
        }
    }

    private fun startFirebaseSync() {
        lifecycleScope.launch {
            checkNotNull(firebaseNoteStore).user.collectLatest { user ->
                if (user == null) return@collectLatest
                try {
                    FirebaseNoteSync(
                        store = checkNotNull(firebaseNoteStore),
                        noteDao = database.noteDao(),
                        ownerUserId = user.uid
                    ).run()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.e(TAG, "Firebase note sync failed.", exception)
                    notesViewModel.showError(
                        exception.localizedMessage ?: "Could not sync notes from Firebase."
                    )
                }
            }
        }
    }

    private fun startFirebaseTestSignIn() {
        val email = BuildConfig.FIREBASE_TEST_EMAIL
        val password = BuildConfig.FIREBASE_TEST_PASSWORD
        if (email.isBlank() || password.isBlank()) return

        val store = checkNotNull(firebaseNoteStore)
        if (store.currentUser != null) return

        store.signIn(email, password) { exception ->
            if (exception != null) {
                Log.e(TAG, "Firebase test-account sign-in failed.", exception)
                notesViewModel.showError(
                    exception.localizedMessage ?: "Could not sign in to the Firebase test account."
                )
            }
        }
    }

    private companion object {
        const val TAG = "StickiesPost"
        const val DATABASE_NAME = "stickiespost_notes.db"
        const val SYNC_PROVIDER_SUPABASE = "SUPABASE"
        const val SYNC_PROVIDER_FIREBASE = "FIREBASE"
    }

    @androidx.compose.runtime.Composable
    private fun NotesAppContent(
        notesViewModel: NotesViewModel,
        tasksViewModel: TasksViewModel,
        currentUserEmail: String? = null,
        onSignOut: (() -> Unit)?
    ) {
        var showingTasks by rememberSaveable { mutableStateOf(false) }
        if (showingTasks) {
            TasksScreen(
                viewModel = tasksViewModel,
                onBack = { showingTasks = false }
            )
        } else {
            NotesScreen(
                viewModel = notesViewModel,
                onOpenTasks = { showingTasks = true },
                currentUserEmail = currentUserEmail,
                onSignOut = onSignOut
            )
        }
    }
}
