package com.tdcolvin.bleclient.ui.screens

import android.bluetooth.BluetoothDevice
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tdcolvin.bleclient.ble.PERMISSION_BLUETOOTH_CONNECT
import com.tdcolvin.bleclient.ble.PERMISSION_BLUETOOTH_SCAN

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

@Preview
@Composable
fun PreviewDeviceItem() {
    DeviceItem(deviceName = "A test BLE device", selectDevice = { })
}