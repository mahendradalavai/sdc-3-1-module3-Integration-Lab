package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var currentUrl: String = ""

    @Volatile
    private var apiInstance: VectorServerApi? = null

    fun getApi(baseUrl: String): VectorServerApi {
        val normalizedUrl = if (!baseUrl.endsWith("/")) "$baseUrl/" else baseUrl
        if (apiInstance == null || currentUrl != normalizedUrl) {
            synchronized(this) {
                currentUrl = normalizedUrl
                val retrofit = Retrofit.Builder()
                    .baseUrl(normalizedUrl)
                    .client(okHttpClient)
                    .addConverterFactory(MoshiConverterFactory.create(moshi))
                    .build()
                apiInstance = retrofit.create(VectorServerApi::class.java)
            }
        }
        return apiInstance!!
    }
}
