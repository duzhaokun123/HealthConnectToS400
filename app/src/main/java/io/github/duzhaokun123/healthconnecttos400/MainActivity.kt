package io.github.duzhaokun123.healthconnecttos400

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.duzhaokun123.healthconnecttos400.databinding.ActivityMainBinding
import io.github.duzhaokun123.s400scale.S400InputData
import io.github.duzhaokun123.s400scale.S400Scale
import io.github.duzhaokun123.s400scale.UserInfo
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import androidx.core.content.edit
import androidx.core.view.updatePadding
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Power
import io.github.duzhaokun123.s400scale.BodyComposition
import java.time.ZoneOffset

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MainActivity"
        const val REQUEST_CODE_1 = 1
    }

    lateinit var healthConnectClient: HealthConnectClient

    var deviceMacAddress = ""
    var deviceBleKey = ""
    var dataOk = false
    lateinit var bodyComposition: BodyComposition

    lateinit var userInfo: UserInfo
    var dataRecorded = false

    val requestHealthPermissions =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {}

    val scanCallback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (dataOk) return
            val device = result.device
            if (device.address.macAddressUniform() == deviceMacAddress.macAddressUniform()) {
                Log.d(TAG, "found device: ${device.address}")
                runOnUiThread {
                    binding.ivDeviceScan.visibility = View.VISIBLE
                    binding.ivDeviceScan.postDelayed({
                        binding.ivDeviceScan.visibility = View.INVISIBLE
                    }, 500)
                }
                val data = result.scanRecord?.serviceData[ParcelUuid.fromString("0000fe95-0000-1000-8000-00805f9b34fb")] ?: byteArrayOf()
                Log.d(TAG, "data: ${data.joinToString(",") { it.toUByte().toString() }}")
                val bodyComposition =
                    runCatching {
                        S400Scale.getBodyComposition(userInfo,
                            S400InputData(
                                macOriginal = deviceMacAddress,
                                aesKey = deviceBleKey,
                                data = data,
                                dataString = "skip"
                            )
                        )
                    }.onFailure {
                        Log.e(TAG, "failed to get body composition", it)
                        getSystemService<BluetoothManager>()!!
                            .adapter
                            .bluetoothLeScanner
                            .stopScan(this)
                        runOnUiThread {
                            binding.viewSwitcher.showPrevious()
                            MaterialAlertDialogBuilder(this@MainActivity)
                                .setTitle("Data Error")
                                .setMessage("Failed to parse data from scale: ${it.message}")
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        }
                    }.getOrNull()
                Log.d(TAG, "bodyComposition: $bodyComposition")
                if (bodyComposition != null) {
                    this@MainActivity.bodyComposition = bodyComposition
                    dataOk = true
                    dataRecorded = false
                    getSystemService<BluetoothManager>()!!
                        .adapter
                        .bluetoothLeScanner
                        .stopScan(this)
                    runOnUiThread {
                        binding.viewSwitcher.showPrevious()
                        binding.tvResult.text = """
                            |weight: ${bodyComposition.weight} kg
                            |bmi: ${bodyComposition.bmi}
                            |fat: ${bodyComposition.fat} kg
                            |muscleMass: ${bodyComposition.muscleMass} kg
                            |water: ${bodyComposition.water} kg
                            |boneMass: ${bodyComposition.boneMass} kg
                            |proteinPercentage: ${bodyComposition.proteinPercentage} %
                            |bmr: ${bodyComposition.bmr} kcal
                            |heartRate: ${bodyComposition.heartRate} bpm
                            |visceralFat: ${bodyComposition.visceralFat}
                            |metabolicAge: ${bodyComposition.metabolicAge} years
                            |bodyType: ${bodyComposition.bodyTypeName} (${bodyComposition.bodyType})
                            |idealWeight: ${bodyComposition.idealWeight} kg
                            |impedance: ${bodyComposition.impedance}
                            |Recorded at: ${bodyComposition.date}
                        """.trimMargin()
                    }
                    PreferenceManager.getDefaultSharedPreferences(this@MainActivity).edit {
                        putInt("height", userInfo.height)
                        putInt("age", userInfo.age)
                        putInt("sex", userInfo.sex.code)
                        putString("device_mac", deviceMacAddress)
                        putString("device_ble_key", deviceBleKey)
                    }
                    if (binding.cbAutoRecord.isChecked) {
                        recordData()
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            toast("scan failed: $errorCode")
        }
    }

    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        runCatching {
            healthConnectClient = HealthConnectClient.getOrCreate(this)
        }.onFailure { t ->
            MaterialAlertDialogBuilder(this)
                .setTitle("Health Connect Error")
                .setMessage("Failed to initialize Health Connect: ${t.message}")
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    finish()
                }
                .setCancelable(false)
                .show()
            return
        }

        binding.btnScan.setOnClickListener {
            if (binding.etHeight.text.toString().toIntOrNull() == null) {
                binding.etHeight.error = "invalid"
                return@setOnClickListener
            }
            if (binding.etAge.text.toString().toIntOrNull() == null) {
                binding.etAge.error = "invalid"
                return@setOnClickListener
            }
            if (binding.rgSex.checkedRadioButtonId == View.NO_ID) {
                toast("please select sex")
                return@setOnClickListener
            }
            if (checkSelfPermission("android.permission.BLUETOOTH_SCAN") != PackageManager.PERMISSION_GRANTED) {
                toast("no BLUETOOTH_SCAN permission")
            } else {
                // Hide the soft keyboard
                getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(currentFocus?.windowToken, 0)

                deviceMacAddress = binding.etDeviceMac.text.toString()
                deviceBleKey = binding.etBleKey.text.toString()
                getSystemService<BluetoothManager>()!!
                    .adapter
                    .bluetoothLeScanner
                    .startScan(scanCallback)
                userInfo = UserInfo(
                    height = binding.etHeight.text.toString().toInt(),
                    age = binding.etAge.text.toString().toInt(),
                    sex = if (binding.rgSex.checkedRadioButtonId == R.id.rb_male) UserInfo.Sex.Male else UserInfo.Sex.Female
                )
                binding.viewSwitcher.showNext()
            }
        }
        binding.btnRequestPermissions.setOnClickListener {
            requestPermissions(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                REQUEST_CODE_1
            )
        }
        binding.ilAge.setEndIconOnClickListener {
            DatePickerDialog(this).apply {
                setOnDateSetListener { _, year, month, day ->
                    val birthDate = LocalDate.of(year, month + 1, day)
                    val today = LocalDate.now()
                    val age = Period.between(birthDate, today).years
                    binding.etAge.setText(age.toString())
                }
                datePicker.maxDate = System.currentTimeMillis()
                datePicker.init(2000, 0, 1, null)
            }.show()
        }
        binding.btnRecord.setOnClickListener {
            if (dataOk.not()) {
                toast("no data")
                return@setOnClickListener
            }
            recordData()
        }

        val preference = PreferenceManager.getDefaultSharedPreferences(this)
        binding.etDeviceMac.setText(preference.getString("device_mac", ""))
        binding.etBleKey.setText(preference.getString("device_ble_key", ""))
        binding.etHeight.setText(preference.getInt("height", -1).toString())
        binding.etAge.setText(preference.getInt("age", -1).toString())
        when (preference.getInt("sex", -1)) {
            UserInfo.Sex.Male.code -> binding.rgSex.check(R.id.rb_male)
            UserInfo.Sex.Female.code -> binding.rgSex.check(R.id.rb_female)
        }

        runBlocking {
            runCatching {
                val heightRecords = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        HeightRecord::class, TimeRangeFilter.before(Instant.now())
                    )
                )
                val latestHeightRecord = heightRecords.records.maxByOrNull { it.time }
                if (latestHeightRecord != null) {
                    binding.etHeight.setText((latestHeightRecord.height.inMeters * 100).toInt().toString())
                }

            }
        }

        window.decorView.setOnApplyWindowInsetsListener { _, insets ->
            val systemBars = insets.getInsets(WindowInsets.Type.systemBars() + WindowInsets.Type.displayCutout())
            binding.toolbar.updatePadding(left = systemBars.left, right = systemBars.right, top = systemBars.top)
            binding.sv.updatePadding(left = systemBars.left, right = systemBars.right, bottom = systemBars.bottom)
            WindowInsets.CONSUMED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_1) {
            requestHealthPermissions.launch(
                setOf(
                    HealthPermission.getReadPermission<HeightRecord>(),
                    HealthPermission.getWritePermission<BasalMetabolicRateRecord>(),
                    HealthPermission.getWritePermission<BodyFatRecord>(),
                    HealthPermission.getWritePermission<BodyWaterMassRecord>(),
                    HealthPermission.getWritePermission<LeanBodyMassRecord>(),
                    HealthPermission.getWritePermission<WeightRecord>(),
                    HealthPermission.getWritePermission<HeartRateRecord>(),
                    HealthPermission.getWritePermission<BoneMassRecord>(),
                )
            )
        }
    }

    fun toast(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 将 MAC 地址转换为统一格式，去除分隔符并转换为大写
     */
    fun String.macAddressUniform(): String {
        return this.replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
    }

    fun recordData() {
        if (dataRecorded) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Data Already Recorded")
                .setMessage("Recording data multiple times may lead to duplicates. Do you want to proceed?")
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    dataRecorded = false
                    recordData()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        val metadata = Metadata.autoRecorded(
            Device(
                type = Device.TYPE_SCALE,
                model = "yunmai.scales.ms103"
            )
        )
        val time = bodyComposition.date.toInstant()
        val records = mutableListOf<Record>()
        records.add(
            WeightRecord(
                time, ZoneOffset.UTC, Mass.kilograms(bodyComposition.weight), metadata
            )
        )
        bodyComposition.bmr?.let { bmr ->
            records.add(
                BasalMetabolicRateRecord(
                    time, ZoneOffset.UTC, Power.kilocaloriesPerDay(bmr), metadata
                )
            )
        }
        bodyComposition.fat?.let { fat ->
            records.add(
                BodyFatRecord(
                    time, ZoneOffset.UTC, Percentage(fat), metadata
                )
            )
        }
        bodyComposition.water?.let { water ->
            records.add(
                BodyWaterMassRecord(
                    time, ZoneOffset.UTC, Mass.kilograms(water), metadata
                )
            )
        }
        bodyComposition.muscleMass?.let { muscleMass ->
            records.add(
                LeanBodyMassRecord(
                    time, ZoneOffset.UTC, Mass.kilograms(muscleMass), metadata
                )
            )
        }
        bodyComposition.boneMass?.let { boneMass ->
            records.add(
                BoneMassRecord(
                    time, ZoneOffset.UTC, Mass.kilograms(boneMass), metadata
                )
            )
        }
        bodyComposition.heartRate?.let { heartRate ->
            if (binding.cbHeartRate.isChecked.not()) return@let
            records.add(
                HeartRateRecord(
                    time, ZoneOffset.UTC,
                    time, ZoneOffset.UTC,
                    listOf(HeartRateRecord.Sample(time, heartRate.toLong())),
                    metadata
                )
            )
        }
        runBlocking {
            runCatching {
                healthConnectClient.insertRecords(records)
            }.onSuccess {
                toast("data recorded")
                dataRecorded = true
            }.onFailure { t ->
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("Record Data Error")
                    .setMessage("Failed to record data: ${t.message}")
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }
}