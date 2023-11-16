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
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tdcolvin.bleclient.ui.theme.BLEClientTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
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
    else if (uiState.activeDevice == null) {
        ScanningScreen(
            isScanning = uiState.isScanning,
            foundDevices = uiState.foundDevices,
            startScanning = viewModel::startScanning,
            stopScanning = viewModel::stopScanning,
            selectDevice = { device ->
                viewModel.stopScanning()
                viewModel.setActiveDevice(device)
            }
        )
    }
    else {
        DeviceScreen(
            unselectDevice = {
                viewModel.disconnectActiveDevice()
                viewModel.setActiveDevice(null)
            },
            isDeviceConnected = uiState.isDeviceConnected,
            discoveredCharacteristics = uiState.discoveredCharacteristics,
            password = uiState.password,
            nameWrittenTimes = uiState.nameWrittenTimes,
            connect = viewModel::connectActiveDevice,
            discoverServices = viewModel::discoverActiveDeviceServices,
            readPassword = viewModel::readPasswordFromActiveDevice,
            writeName = viewModel::writeNameToActiveDevice
        )
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
    stopScanning: () -> Unit,
    selectDevice: (BluetoothDevice) -> Unit
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
                DeviceItem(
                    deviceName = device.name,
                    selectDevice = { selectDevice(device) }
                )
            }
        }
    }
}

@Composable
fun DeviceItem(deviceName: String?, selectDevice: () -> Unit) {
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
            Button(onClick = selectDevice) {
                Text("Connect")
            }
        }
    }
}

@Composable
fun DeviceScreen(
    unselectDevice: () -> Unit,
    isDeviceConnected: Boolean,
    discoveredCharacteristics: Map<String, List<String>>,
    password: String?,
    nameWrittenTimes: Int,
    connect: () -> Unit,
    discoverServices: () -> Unit,
    readPassword: () -> Unit,
    writeName: () -> Unit
) {
    val foundTargetService = discoveredCharacteristics.contains(CTF_SERVICE_UUID.toString())

    Column(
        Modifier.scrollable(rememberScrollState(), Orientation.Vertical)
    ) {
        Button(onClick = connect) {
            Text("1. Connect")
        }
        Text("Device connected: $isDeviceConnected")
        Button(onClick = discoverServices, enabled = isDeviceConnected) {
            Text("2. Discover Services")
        }
        LazyColumn {
            items(discoveredCharacteristics.keys.sorted()) { serviceUuid ->
                Text(text = serviceUuid, fontWeight = FontWeight.Black)
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    discoveredCharacteristics[serviceUuid]?.forEach {
                        Text(it)
                    }
                }
            }
        }
        Button(onClick = readPassword, enabled = isDeviceConnected && foundTargetService) {
            Text("3. Read Password")
        }
        if (password != null) {
            Text("Found password: $password")
        }
        Button(onClick = writeName, enabled = isDeviceConnected && foundTargetService) {
            Text("4. Write Your Name")
        }
        if (nameWrittenTimes > 0) {
            Text("Number of times name sent: $nameWrittenTimes")
        }

        OutlinedButton(modifier = Modifier.padding(top = 40.dp),  onClick = unselectDevice) {
            Text("Disconnect")
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BLEClientViewModel(private val application: Application): AndroidViewModel(application) {
    private val bleClient = BLEClient(application)
    private var activeConnection = MutableStateFlow<BLEDeviceConnection?>(null)

    private val isDeviceConnected = activeConnection.flatMapLatest { it?.isConnected ?: flowOf(false) }
    private val activeDeviceServices = activeConnection.flatMapLatest {
        it?.services ?: flowOf(emptyList())
    }
    private val activeDevicePassword = activeConnection.flatMapLatest {
        it?.passwordRead ?: flowOf(null)
    }

    private val _uiState = MutableStateFlow(BLEClientUIState())
    val uiState = combine(_uiState, isDeviceConnected, activeDeviceServices, activeDevicePassword) { state, isDeviceConnected, services, password ->
        state.copy(
            isDeviceConnected = isDeviceConnected,
            discoveredCharacteristics = services.associate { service -> Pair(service.uuid.toString(), service.characteristics.map { it.uuid.toString() }) },
            password = password
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BLEClientUIState())

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

    @SuppressLint("MissingPermission")
    @RequiresPermission(allOf = [PERMISSION_BLUETOOTH_CONNECT, PERMISSION_BLUETOOTH_SCAN])
    fun setActiveDevice(device: BluetoothDevice?) {
        activeConnection.value = device?.run { BLEDeviceConnection(application, device) }
        _uiState.update { it.copy(activeDevice = device, nameWrittenTimes = 0) }
    }

    @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
    fun connectActiveDevice() {
        activeConnection.value?.connect()
    }

    @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
    fun disconnectActiveDevice() {
        activeConnection.value?.disconnect()
    }

    @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
    fun discoverActiveDeviceServices() {
        activeConnection.value?.discoverServices()
    }

    @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
    fun readPasswordFromActiveDevice() {
        activeConnection.value?.readPassword()
    }

    @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
    fun writeNameToActiveDevice() {
        activeConnection.value?.writeName()
        _uiState.update { it.copy(nameWrittenTimes = it.nameWrittenTimes + 1) }
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
    val activeDevice: BluetoothDevice? = null,
    val isDeviceConnected: Boolean = false,
    val discoveredCharacteristics: Map<String, List<String>> = emptyMap(),
    val password: String? = null,
    val nameWrittenTimes: Int = 0
)

@Preview
@Composable
fun PreviewDeviceItem() {
    DeviceItem(deviceName = "A test BLE device", selectDevice = { })
}