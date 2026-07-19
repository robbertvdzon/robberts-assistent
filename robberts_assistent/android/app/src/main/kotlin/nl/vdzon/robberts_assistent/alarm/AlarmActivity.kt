package nl.vdzon.robberts_assistent.alarm

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import nl.vdzon.robberts_assistent.R

/**
 * Full-screen wekker-scherm dat over het lockscreen verschijnt (setShowWhenLocked + setTurnScreenOn)
 * en het scherm aanzet. Toont de alarmtekst met knoppen **Sluit** en **Snooze**; die sturen de actie
 * door naar [AlarmService] (die het geluid stopt of het alarm opnieuw inplant).
 */
class AlarmActivity : Activity() {

    private var alarmId: Int = 0
    private var message: String = "Alarm"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockscreen()
        setContentView(R.layout.activity_alarm)
        bind(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bind(intent)
    }

    private fun bind(intent: Intent) {
        alarmId = intent.getIntExtra(AlarmScheduling.EXTRA_ID, 0)
        message = intent.getStringExtra(AlarmScheduling.EXTRA_MESSAGE) ?: "Alarm"
        findViewById<TextView>(R.id.alarm_message).text = message
        findViewById<Button>(R.id.btn_snooze).setOnClickListener { act(AlarmService.ACTION_SNOOZE) }
        findViewById<Button>(R.id.btn_dismiss).setOnClickListener { act(AlarmService.ACTION_DISMISS) }
    }

    private fun act(action: String) {
        startService(Intent(this, AlarmService::class.java).apply { this.action = action })
        finish()
    }

    private fun showOverLockscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
                .requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // Terugknop mag de wekker niet zomaar wegklikken zonder Sluit/Snooze.
    override fun onBackPressed() {}
}
