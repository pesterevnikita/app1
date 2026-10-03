package io.github.pesterevnikita.focusgate.runtime
import android.app.*
import android.content.*
import io.github.pesterevnikita.focusgate.MainActivity
import io.github.pesterevnikita.focusgate.data.AppState
object RemainingTimeNotification {
    private var lastText=""
    fun update(context: Context,state: AppState,remaining: Map<String,Long>) {
        val manager=context.getSystemService(NotificationManager::class.java)
        if(!state.countdown) {manager.cancel(10); lastText=""; return}
        if(!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(NotificationChannel("usage","Remaining usage",NotificationManager.IMPORTANCE_LOW))
        val text=state.policy.blockers.filter{it.enabled && it.quotaGroupId!=null}.joinToString(" · "){"${it.name}: ${((remaining[it.quotaGroupId] ?: 0)+59999)/60000} min"}.take(240)
        if(text==lastText) return
        lastText=text
        val open=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        manager.notify(10,Notification.Builder(context,"usage").setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("FocusGate").setContentText(text.ifBlank{"No enabled quota blockers"}).setContentIntent(open).setOngoing(true).build())
    }
}
