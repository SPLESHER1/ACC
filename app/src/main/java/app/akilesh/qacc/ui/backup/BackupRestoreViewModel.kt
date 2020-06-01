package app.akilesh.qacc.ui.backup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.akilesh.qacc.model.BackupFile
import app.akilesh.qacc.utils.workers.RestoreWorker
import java.util.*

class BackupRestoreViewModel(application: Application) : AndroidViewModel(application) {

    val backupFiles: MutableLiveData<MutableList<BackupFile>> by lazy {
        MutableLiveData<MutableList<BackupFile>>()
    }

    val workManager = WorkManager.getInstance(application)
    internal val outputWorkInfo: LiveData<List<WorkInfo>>
    var restoreWorkerId: UUID? = null
    private val tag = "restore"
    init {
        outputWorkInfo = workManager.getWorkInfosByTagLiveData(tag)
    }

    internal fun restore(filesList: Array<String>) {
        val builder = Data.Builder()
        builder.putStringArray("files", filesList)
        val restoreRequest = OneTimeWorkRequestBuilder<RestoreWorker>()
            .setInputData(builder.build())
            .addTag(tag)
            .build()
        restoreWorkerId = restoreRequest.id
        workManager.enqueue(restoreRequest)
    }
}