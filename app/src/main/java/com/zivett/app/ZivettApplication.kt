package com.zivett.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.zivett.app.app.AppConfig
import com.zivett.app.core.PendingReferral
import com.zivett.app.core.push.PushRegistration
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.osmdroid.config.Configuration

class ZivettApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        PendingReferral.attach(this)
        PushRegistration.attach(this)
        // OpenStreetMap's tile policy wants a real user agent (the same
        // tiles the web's Leaflet maps use).
        Configuration.getInstance().userAgentValue = "zivett-android/${BuildConfig.VERSION_NAME}"
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val client = OkHttpClient.Builder().apply {
            if (BuildConfig.BACKEND_SWITCHER) addInterceptor(LocalHostRewrite(context))
        }.build()
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .build()
    }
}

/// Debug builds only: the local Sail backend stamps absolute
/// `http://localhost:8090/storage/...` URLs on photos and avatars, and
/// "localhost" on an emulator or a phone is the device itself. Point
/// those at whichever backend the Welcome screen's Server menu picked
/// (10.0.2.2 or the Mac's LAN name). Release builds never see localhost.
private class LocalHostRewrite(private val context: PlatformContext) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val host = request.url.host
        if (host != "localhost" && host != "127.0.0.1") return chain.proceed(request)
        val base = runCatching { AppConfig.current(context).apiBaseUrl.toHttpUrl() }.getOrNull()
            ?: return chain.proceed(request)
        if (base.host == host) return chain.proceed(request)
        val rewritten = request.url.newBuilder().scheme(base.scheme).host(base.host).port(base.port).build()
        return chain.proceed(request.newBuilder().url(rewritten).build())
    }
}
