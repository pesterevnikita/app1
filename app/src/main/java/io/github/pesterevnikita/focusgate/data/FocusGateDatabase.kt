package io.github.pesterevnikita.focusgate.data
import androidx.room.*

// One versioned document is committed atomically: lock state can never get ahead of its policy.
@Entity(tableName="app_state") data class StateEntity(@PrimaryKey val id: Int = 1, val json: String)
@Dao interface StateDao {
    @Query("SELECT * FROM app_state WHERE id = 1") suspend fun read(): StateEntity?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun write(state: StateEntity)
}
@Database(entities=[StateEntity::class],version=1,exportSchema=true)
abstract class FocusGateDatabase: RoomDatabase() { abstract fun stateDao(): StateDao }
