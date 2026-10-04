package in.prabhupaints.vksnative;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "transactions",
    indices = {@Index(value = {"sourceId"}, unique = true)}
)
public class TransactionEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String sourceId;
    public String financialYear;
    public String dateText;
    public String particular;
    public String category;
    public Long creditPaise;
    public Long debitPaise;
    public Long sourceBalancePaise;
    public String notes;
    public String sourceSheet;
    public int sourceRow;
    public String sourceType;
    public long createdAt;
    public long updatedAt;
    public String rawJson;
    public String photoPath;
}
