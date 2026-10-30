package com.example.stickiespost

import android.app.Application
import com.example.stickiespost.data.remote.FirebaseNoteStore
import com.google.firebase.firestore.FirebaseFirestore

class StickiesPostApplication : Application() {
    val firebaseNoteStore by lazy {
        FirebaseNoteStore(FirebaseFirestore.getInstance())
    }
}
