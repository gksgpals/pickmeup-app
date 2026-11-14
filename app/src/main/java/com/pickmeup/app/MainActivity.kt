package com.pickmeup.app

import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.pickmeup.app.ui.PickMeUpApp
import com.pickmeup.app.ui.PickupViewModel
import com.pickmeup.app.ui.theme.PickMeUpTheme

/**
 * 픽미업 진입 화면.
 * - 화면 방향은 고정하지 않습니다(Pleos 멀티 윈도우: portrait/landscape 모두 반응형 레이아웃).
 * - 마이크는 Pleos Speech Service가 소유하므로 앱은 RECORD_AUDIO 권한을 요청하지 않습니다.
 * - Pleos 차량·음성·내비 권한은 매니페스트 선언으로 허용되는 것이 기본이지만, 실행 환경에서
 *   런타임(dangerous) 권한으로 정의된 경우를 대비해 시작 시 한 번 요청한 뒤 플랫폼을 연결합니다.
 * - 플랫폼 연동 해제는 PickupViewModel.onCleared()에서 처리합니다.
 */
class MainActivity : ComponentActivity() {

    private val vm: PickupViewModel by viewModels()

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            Log.i("PickMeUp", "Pleos 권한 요청 결과: $result")
            showApp()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pending = if (savedInstanceState == null) runtimePleosPermissionsToRequest() else emptyList()
        if (pending.isEmpty()) showApp() else permissionRequest.launch(pending.toTypedArray())
    }

    private fun showApp() {
        setContent {
            PickMeUpTheme {
                PickMeUpApp(vm)
            }
        }
    }

    /** 매니페스트에 선언한 pleos.* 권한 중 이 기기에서 런타임 권한이고 아직 허용되지 않은 것. */
    private fun runtimePleosPermissionsToRequest(): List<String> = try {
        val declared = packageManager
            .getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty()
            .filter { it.startsWith("pleos.") }
        declared.filter { name ->
            val info = runCatching { packageManager.getPermissionInfo(name, 0) }.getOrNull()
                ?: return@filter false // 이 기기에 정의되지 않은 권한(비 Pleos 기기)
            val dangerous = (info.protection and PermissionInfo.PROTECTION_DANGEROUS) != 0
            dangerous && ContextCompat.checkSelfPermission(this, name) != PackageManager.PERMISSION_GRANTED
        }
    } catch (t: Throwable) {
        Log.w("PickMeUp", "권한 확인 실패", t)
        emptyList()
    }
}
