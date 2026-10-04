package in.prabhupaints.vksnative;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class CloudBackupWorker extends Worker {
    public CloudBackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            LedgerDatabase db = LedgerDatabase.get(getApplicationContext());
            BackupUtils.ensureSeeded(getApplicationContext(), db);
            boolean ok = BackupUtils.writeCloudBackup(getApplicationContext(), db);
            return ok || !BackupUtils.hasCloudFolder(getApplicationContext()) ? Result.success() : Result.retry();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}
