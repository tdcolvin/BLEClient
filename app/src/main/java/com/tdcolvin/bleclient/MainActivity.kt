package com.tdcolvin.bleclient

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tdcolvin.bleclient.ui.theme.BLEClientTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BLEClientTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                   MainScreen()
                }
            }
        }
    }
}

val ALL_BLE_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN
    )
}
else {
    arrayOf(
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
}

private fun haveAllPermissions(context: Context) =
    ALL_BLE_PERMISSIONS
        .all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

@SuppressLint("MissingPermission")
@Composable
fun MainScreen(viewModel: BLEClientViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var allPermissionsGranted by remember {
        mutableStateOf (haveAllPermissions(context))
    }

    if (!allPermissionsGranted) {
        PermissionsRequiredScreen { allPermissionsGranted = true }
    }
    else if (uiState.selectedDevice == null) {
        ScanningScreen(
            uiState.isScanning,
            uiState.foundDevices,
            viewModel::startScanning,
            viewModel::stopScanning
        )
    }
    else {
        DeviceScreen()
    }
}

@Composable
fun PermissionsRequiredScreen(onPermissionGranted: () -> Unit) {
    Box {
        Column(
            modifier = Modifier.align(Alignment.Center)
        ) {
            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { granted ->
                    if (granted.values.all { it }) {
                        onPermissionGranted()
                    }
                }
            Button(onClick = { launcher.launch(ALL_BLE_PERMISSIONS) }) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
@RequiresPermission(allOf = [PERMISSION_BLUETOOTH_SCAN, PERMISSION_BLUETOOTH_CONNECT])
fun ScanningScreen(
    isScanning: Boolean,
    foundDevices: List<BluetoothDevice>,
    startScanning: () -> Unit,
    stopScanning: () -> Unit
) {
    Column (
        Modifier.padding(horizontal = 10.dp)
    ){
        if (isScanning) {
            Text("Scanning...")

            Button(onClick = stopScanning) {
                Text("Stop Scanning")
            }
        }
        else {
            Button(onClick = startScanning) {
                Text("Start Scanning")
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(foundDevices) { device ->
                DeviceItem(deviceName = device.name)
            }
        }
    }
}

@Composable
fun DeviceItem(deviceName: String?) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = deviceName ?: "[Unnamed]",
                textAlign = TextAlign.Center,
            )
            Button(onClick = { }) {
                Text("Connect")
            }
        }
    }
}

@Composable
fun DeviceScreen() {

}

class BLEClientViewModel(application: Application): AndroidViewModel(application) {
    private val bleClient = BLEClient(application)

    private val _uiState = MutableStateFlow(BLEClientUIState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            bleClient.foundDevices.collect { devices ->
                _uiState.update { it.copy(foundDevices = devices) }
            }
        }
        viewModelScope.launch {
            bleClient.isScanning.collect { isScanning ->
                _uiState.update { it.copy(isScanning = isScanning) }
            }
        }
    }

    @RequiresPermission(PERMISSION_BLUETOOTH_SCAN)
    fun startScanning() {
        bleClient.startScanning()
    }

    @RequiresPermission(PERMISSION_BLUETOOTH_SCAN)
    fun stopScanning() {
        bleClient.stopScanning()
    }

    fun selectDevice(device: BluetoothDevice?) {
        _uiState.update { it.copy(selectedDevice = device) }
    }

    override fun onCleared() {
        super.onCleared()

        //when the ViewModel dies, shut down the BLE client with it
        if (bleClient.isScanning.value) {
            if (ActivityCompat.checkSelfPermission(
                    getApplication(),
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                bleClient.stopScanning()
            }
        }
    }
}

data class BLEClientUIState(
    val isScanning: Boolean = false,
    val foundDevices: List<BluetoothDevice> = emptyList(),
    val selectedDevice: BluetoothDevice? = null
)

@Preview
@Composable
fun PreviewDeviceItem() {
    DeviceItem(deviceName = "A test BLE device")
}