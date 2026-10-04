package in.prabhupaints.vksnative;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface LedgerDao {
    @Query("SELECT COUNT(*) FROM transactions")
    int count();

    @Query("SELECT * FROM transactions ORDER BY id ASC")
    List<TransactionEntity> getAll();

    @Query("SELECT * FROM transactions WHERE id=:id LIMIT 1")
    TransactionEntity getById(long id);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(TransactionEntity item);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAll(List<TransactionEntity> items);

    @Update
    void update(TransactionEntity item);

    @Delete
    void delete(TransactionEntity item);

    @Query("DELETE FROM transactions")
    void clearAll();
}
