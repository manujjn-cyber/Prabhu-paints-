package in.prabhupaints.vksnative;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {TransactionEntity.class}, version = 1, exportSchema = false)
public abstract class LedgerDatabase extends RoomDatabase {
    public abstract LedgerDao ledgerDao();

    private static volatile LedgerDatabase INSTANCE;

    public static LedgerDatabase get(Context context) {
        if (INSTANCE == null) {
            synchronized (LedgerDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            LedgerDatabase.class,
                            "vks_native_ledger.db"
                    ).build();
                }
            }
        }
        return INSTANCE;
    }
}
