package com.pickmeup.app.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 동승자 알림.
 * ① 픽미업 문자 서버(SMS_SERVER_URL 설정 시) → POST {to, text}
 * ② 차량 문자 앱 → ACTION_SENDTO "smsto:" (운전자가 문자 앱에서 확인 후 전송, SEND_SMS 권한 사용 안 함)
 * ③ 둘 다 불가 → Fallback: 화면에 합류 안내 문구를 띄워 정차 중 직접 전달
 */
class AndroidPassengerNotifier(
    context: Context,
    private val smsServerUrl: String,
) : PassengerNotifier {

    private val appContext = context.applicationContext

    override suspend fun notify(phoneNumber: String?, message: String): NotifyResult {
        val number = phoneNumber?.filter { it.isDigit() || it == '+' }.orEmpty()

        if (smsServerUrl.isNotBlank() && number.isNotBlank()) {
            if (postToServer(number, message)) return NotifyResult.Sent("픽미업 서버 문자")
        }

        if (number.isNotBlank()) {
            if (openSmsApp(number, message)) return NotifyResult.Sent("차량 문자 앱")
            return NotifyResult.Fallback("문자 앱을 열 수 없어요", message)
        }

        return NotifyResult.Fallback("동승자 연락처가 없어요", message)
    }

    private suspend fun postToServer(to: String, text: String): Boolean = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(smsServerUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            val body = JSONObject().put("to", to).put("text", text).toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) Log.w(TAG, "문자 서버 응답 $code")
            code in 200..299
        } catch (t: Throwable) {
            Log.w(TAG, "문자 서버 전송 실패", t)
            false
        } finally {
            conn?.disconnect()
        }
    }

    private suspend fun openSmsApp(number: String, text: String): Boolean = withContext(Dispatchers.Main) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).apply {
                putExtra("sms_body", text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "문자 앱 없음", e)
            false
        } catch (t: Throwable) {
            Log.w(TAG, "문자 앱 실행 실패", t)
            false
        }
    }

    private companion object {
        const val TAG = "PickMeUp"
        const val TIMEOUT_MS = 5_000
    }
}
