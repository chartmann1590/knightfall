package com.chartmann.knightfall.data

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {

    val currentUser: FirebaseUser? get() = auth.currentUser
    val uid: String? get() = auth.currentUser?.uid
    val isAnonymous: Boolean get() = auth.currentUser?.isAnonymous ?: true

    val userFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signInAnonymously(): FirebaseUser =
        auth.signInAnonymously().await().user ?: error("Anonymous sign-in failed")

    suspend fun signUpWithEmail(email: String, password: String): FirebaseUser =
        auth.createUserWithEmailAndPassword(email, password).await().user
            ?: error("Sign-up failed")

    suspend fun signInWithEmail(email: String, password: String): FirebaseUser =
        auth.signInWithEmailAndPassword(email, password).await().user
            ?: error("Sign-in failed")

    /** Upgrades a guest account to a full account, keeping uid + stats. */
    suspend fun linkAnonymousToEmail(email: String, password: String): FirebaseUser {
        val user = auth.currentUser ?: error("Not signed in")
        val credential = EmailAuthProvider.getCredential(email, password)
        return user.linkWithCredential(credential).await().user ?: error("Link failed")
    }

    fun signOut() = auth.signOut()

    suspend fun deleteAccount() {
        auth.currentUser?.delete()?.await()
    }
}
