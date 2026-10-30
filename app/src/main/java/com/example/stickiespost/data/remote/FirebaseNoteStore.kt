package com.example.stickiespost.data.remote

import android.util.Log
import com.example.stickiespost.data.local.NoteEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FirebaseNoteStore(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val mutableUser = MutableStateFlow(auth.currentUser)
    val user = mutableUser.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        mutableUser.value = firebaseAuth.currentUser
    }

    init {
        auth.addAuthStateListener(authStateListener)
    }

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun signIn(email: String, password: String, onComplete: (Exception?) -> Unit) {
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnCompleteListener { task -> onComplete(task.exception) }
    }

    fun signUp(email: String, password: String, onComplete: (Exception?) -> Unit) {
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnCompleteListener { task -> onComplete(task.exception) }
    }

    fun signOut() {
        auth.signOut()
    }

    fun upsert(note: NoteEntity) {
        val remoteId = note.remoteId
            ?: throw IllegalArgumentException("Firebase note is missing its remote ID.")
        val ownerUserId = note.ownerUserId
            ?: throw IllegalArgumentException("Firebase note is missing its owner.")
        check(currentUser?.uid == ownerUserId) {
            "Cannot sync a note owned by another Firebase account."
        }
        firestore.collection(NOTES_COLLECTION)
            .document(remoteId)
            .set(
                mapOf(
                    "text" to note.text,
                    "category" to note.category,
                    "createdAtMillis" to note.createdAtMillis,
                    "reminderAtMillis" to note.reminderAtMillis,
                    "status" to note.status,
                    OWNER_FIELD to ownerUserId
                )
            )
            .addOnFailureListener { exception ->
                Log.e(TAG, "Could not sync note $remoteId to Firestore.", exception)
            }
    }

    fun delete(remoteId: String, ownerUserId: String) {
        check(currentUser?.uid == ownerUserId) {
            "Cannot delete a note owned by another Firebase account."
        }
        firestore.collection(NOTES_COLLECTION)
            .document(remoteId)
            .delete()
            .addOnFailureListener { exception ->
                Log.e(TAG, "Could not delete note $remoteId from Firestore.", exception)
            }
    }

    internal fun collectionForUser(userId: String): Query {
        check(currentUser?.uid == userId) { "A matching Firebase account must be signed in." }
        return firestore.collection(NOTES_COLLECTION)
            .whereEqualTo(OWNER_FIELD, userId)
    }

    fun dispose() {
        auth.removeAuthStateListener(authStateListener)
    }

    internal companion object {
        const val TAG = "FirebaseNoteStore"
        const val NOTES_COLLECTION = "notes"
        const val OWNER_FIELD = "ownerUserId"
    }
}
