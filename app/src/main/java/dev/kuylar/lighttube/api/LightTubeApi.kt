package dev.kuylar.lighttube.api

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.kuylar.lighttube.api.models.ApiResponse
import dev.kuylar.lighttube.api.models.InstanceInfo
import dev.kuylar.lighttube.api.models.LightTubePlayer
import dev.kuylar.lighttube.api.models.LightTubeUserInfo
import dev.kuylar.lighttube.api.models.LightTubeVideo
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder


class LightTubeApi(context: Context) {
	private val tag = "LightTubeApi"
	private val client = OkHttpClient()
	private val gson = Gson()

	val host: String
	private val refreshToken: String?

	init {
		val sp = context.getSharedPreferences("main", Context.MODE_PRIVATE)
		host = sp.getString("instanceHost", "")!!
		refreshToken = sp.getString("refreshToken", null)
		Log.i(
			tag,
			"Initialized the API for $host ${if (refreshToken != null) "with" else "without"} a refresh token"
		)
	}

	private fun <T> get(
		token: TypeToken<ApiResponse<T>>,
		path: String,
		query: HashMap<String, String> = HashMap()
	): ApiResponse<T> {
		val request: Request = Request.Builder().apply {
			url("$host/api/$path${query.toUrl()}")
			if (refreshToken != null) {
				header("Authorization", "Bearer ${UtilityApi.getToken(host, refreshToken)}")
			}
		}.build()

		client.newCall(request).execute().use { response ->
			val r = gson.fromJson<ApiResponse<T>>(
				response.body!!.string(),
				token.type
			)
			if (r.error != null)
				throw r.error.getException()
			if (r.data == null)
				throw Exception("[0] Received null data")
			return r
		}
	}

	fun getCurrentUser(): ApiResponse<LightTubeUserInfo> {
		return get(
			object : TypeToken<ApiResponse<LightTubeUserInfo>>() {},
			"currentUser"
		)
	}

	fun getInstanceInfo(): InstanceInfo {
		val request: Request = Request.Builder()
			.url("$host/api/info")
			.build()

		client.newCall(request).execute().use { response ->
			if (response.code != 200)
				throw Exception("HTTP ${response.code} while trying to get instance info")
			return gson.fromJson(
				response.body!!.string(),
				InstanceInfo::class.java
			)
		}
	}

	fun getPlayer(id: String): ApiResponse<LightTubePlayer> {
		return get(
			object : TypeToken<ApiResponse<LightTubePlayer>>() {},
			"player",
			hashMapOf(Pair("id", id))
		)
	}

	fun getVideo(id: String, playlistId: String?): ApiResponse<LightTubeVideo> {
		val data = hashMapOf(Pair("id", id))
		if (playlistId != null)
			data["playlistId"] = playlistId
		return get(
			object : TypeToken<ApiResponse<LightTubeVideo>>() {},
			"video",
			data
		)
	}
}

private fun <K, V> HashMap<K, V>.toUrl(): String {
	var res = "?"
	forEach {
		res += "${
			URLEncoder.encode(
				it.key as String,
				"utf8"
			)
		}=${URLEncoder.encode(it.value as String, "utf8")}&"
	}
	return res.trimEnd('&')
}