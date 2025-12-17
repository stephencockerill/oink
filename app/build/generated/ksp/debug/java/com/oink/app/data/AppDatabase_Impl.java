package com.oink.app.data;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile CheckInDao _checkInDao;

  private volatile CashOutDao _cashOutDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `check_ins` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` INTEGER NOT NULL, `didExercise` INTEGER NOT NULL, `balanceAfter` REAL NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_check_ins_date` ON `check_ins` (`date`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `cash_outs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `amount` REAL NOT NULL, `emoji` TEXT NOT NULL, `cashedOutAt` INTEGER NOT NULL, `balanceBefore` REAL NOT NULL, `balanceAfter` REAL NOT NULL, `exerciseRewardAtTime` REAL NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '74e1d9b0222db5465400676d2305dd0a')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `check_ins`");
        db.execSQL("DROP TABLE IF EXISTS `cash_outs`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsCheckIns = new HashMap<String, TableInfo.Column>(4);
        _columnsCheckIns.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCheckIns.put("date", new TableInfo.Column("date", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCheckIns.put("didExercise", new TableInfo.Column("didExercise", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCheckIns.put("balanceAfter", new TableInfo.Column("balanceAfter", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysCheckIns = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesCheckIns = new HashSet<TableInfo.Index>(1);
        _indicesCheckIns.add(new TableInfo.Index("index_check_ins_date", true, Arrays.asList("date"), Arrays.asList("ASC")));
        final TableInfo _infoCheckIns = new TableInfo("check_ins", _columnsCheckIns, _foreignKeysCheckIns, _indicesCheckIns);
        final TableInfo _existingCheckIns = TableInfo.read(db, "check_ins");
        if (!_infoCheckIns.equals(_existingCheckIns)) {
          return new RoomOpenHelper.ValidationResult(false, "check_ins(com.oink.app.data.CheckIn).\n"
                  + " Expected:\n" + _infoCheckIns + "\n"
                  + " Found:\n" + _existingCheckIns);
        }
        final HashMap<String, TableInfo.Column> _columnsCashOuts = new HashMap<String, TableInfo.Column>(8);
        _columnsCashOuts.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCashOuts.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCashOuts.put("amount", new TableInfo.Column("amount", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCashOuts.put("emoji", new TableInfo.Column("emoji", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCashOuts.put("cashedOutAt", new TableInfo.Column("cashedOutAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCashOuts.put("balanceBefore", new TableInfo.Column("balanceBefore", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCashOuts.put("balanceAfter", new TableInfo.Column("balanceAfter", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCashOuts.put("exerciseRewardAtTime", new TableInfo.Column("exerciseRewardAtTime", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysCashOuts = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesCashOuts = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoCashOuts = new TableInfo("cash_outs", _columnsCashOuts, _foreignKeysCashOuts, _indicesCashOuts);
        final TableInfo _existingCashOuts = TableInfo.read(db, "cash_outs");
        if (!_infoCashOuts.equals(_existingCashOuts)) {
          return new RoomOpenHelper.ValidationResult(false, "cash_outs(com.oink.app.data.CashOut).\n"
                  + " Expected:\n" + _infoCashOuts + "\n"
                  + " Found:\n" + _existingCashOuts);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "74e1d9b0222db5465400676d2305dd0a", "22ce2f106967c694bb98ff402232d68f");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "check_ins","cash_outs");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `check_ins`");
      _db.execSQL("DELETE FROM `cash_outs`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(CheckInDao.class, CheckInDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(CashOutDao.class, CashOutDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public CheckInDao checkInDao() {
    if (_checkInDao != null) {
      return _checkInDao;
    } else {
      synchronized(this) {
        if(_checkInDao == null) {
          _checkInDao = new CheckInDao_Impl(this);
        }
        return _checkInDao;
      }
    }
  }

  @Override
  public CashOutDao cashOutDao() {
    if (_cashOutDao != null) {
      return _cashOutDao;
    } else {
      synchronized(this) {
        if(_cashOutDao == null) {
          _cashOutDao = new CashOutDao_Impl(this);
        }
        return _cashOutDao;
      }
    }
  }
}
