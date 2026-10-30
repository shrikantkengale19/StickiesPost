package com.example.stickiespost.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.SupabaseClient

object SupabaseClientFactory {
    fun create(projectUrl: String, publishableKey: String): SupabaseClient =
        createSupabaseClient(
            supabaseUrl = projectUrl,
            supabaseKey = publishableKey
        ) {
            install(Postgrest)
            install(Realtime)
        }
}
