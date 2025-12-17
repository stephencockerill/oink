package com.oink.app.data;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.IllegalStateException;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class CheckInDao_Impl implements CheckInDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<CheckIn> __insertionAdapterOfCheckIn;

  private final Converters __converters = new Converters();

  private final EntityDeletionOrUpdateAdapter<CheckIn> __updateAdapterOfCheckIn;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public CheckInDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfCheckIn = new EntityInsertionAdapter<CheckIn>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `check_ins` (`id`,`date`,`didExercise`,`balanceAfter`) VALUES (nullif(?, 0),?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CheckIn entity) {
        statement.bindLong(1, entity.getId());
        final Long _tmp = __converters.fromLocalDate(entity.getDate());
        if (_tmp == null) {
          statement.bindNull(2);
        } else {
          statement.bindLong(2, _tmp);
        }
        final int _tmp_1 = entity.getDidExercise() ? 1 : 0;
        statement.bindLong(3, _tmp_1);
        statement.bindDouble(4, entity.getBalanceAfter());
      }
    };
    this.__updateAdapterOfCheckIn = new EntityDeletionOrUpdateAdapter<CheckIn>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `check_ins` SET `id` = ?,`date` = ?,`didExercise` = ?,`balanceAfter` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CheckIn entity) {
        statement.bindLong(1, entity.getId());
        final Long _tmp = __converters.fromLocalDate(entity.getDate());
        if (_tmp == null) {
          statement.bindNull(2);
        } else {
          statement.bindLong(2, _tmp);
        }
        final int _tmp_1 = entity.getDidExercise() ? 1 : 0;
        statement.bindLong(3, _tmp_1);
        statement.bindDouble(4, entity.getBalanceAfter());
        statement.bindLong(5, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM check_ins";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final CheckIn checkIn, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfCheckIn.insertAndReturnId(checkIn);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final CheckIn checkIn, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfCheckIn.handle(checkIn);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<CheckIn>> getAllCheckInsFlow() {
    final String _sql = "SELECT * FROM check_ins ORDER BY date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"check_ins"}, new Callable<List<CheckIn>>() {
      @Override
      @NonNull
      public List<CheckIn> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfDidExercise = CursorUtil.getColumnIndexOrThrow(_cursor, "didExercise");
          final int _cursorIndexOfBalanceAfter = CursorUtil.getColumnIndexOrThrow(_cursor, "balanceAfter");
          final List<CheckIn> _result = new ArrayList<CheckIn>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CheckIn _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final LocalDate _tmpDate;
            final Long _tmp;
            if (_cursor.isNull(_cursorIndexOfDate)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(_cursorIndexOfDate);
            }
            final LocalDate _tmp_1 = __converters.toLocalDate(_tmp);
            if (_tmp_1 == null) {
              throw new IllegalStateException("Expected NON-NULL 'java.time.LocalDate', but it was NULL.");
            } else {
              _tmpDate = _tmp_1;
            }
            final boolean _tmpDidExercise;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfDidExercise);
            _tmpDidExercise = _tmp_2 != 0;
            final double _tmpBalanceAfter;
            _tmpBalanceAfter = _cursor.getDouble(_cursorIndexOfBalanceAfter);
            _item = new CheckIn(_tmpId,_tmpDate,_tmpDidExercise,_tmpBalanceAfter);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getAllCheckInsAsc(final Continuation<? super List<CheckIn>> $completion) {
    final String _sql = "SELECT * FROM check_ins ORDER BY date ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<CheckIn>>() {
      @Override
      @NonNull
      public List<CheckIn> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfDidExercise = CursorUtil.getColumnIndexOrThrow(_cursor, "didExercise");
          final int _cursorIndexOfBalanceAfter = CursorUtil.getColumnIndexOrThrow(_cursor, "balanceAfter");
          final List<CheckIn> _result = new ArrayList<CheckIn>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CheckIn _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final LocalDate _tmpDate;
            final Long _tmp;
            if (_cursor.isNull(_cursorIndexOfDate)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(_cursorIndexOfDate);
            }
            final LocalDate _tmp_1 = __converters.toLocalDate(_tmp);
            if (_tmp_1 == null) {
              throw new IllegalStateException("Expected NON-NULL 'java.time.LocalDate', but it was NULL.");
            } else {
              _tmpDate = _tmp_1;
            }
            final boolean _tmpDidExercise;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfDidExercise);
            _tmpDidExercise = _tmp_2 != 0;
            final double _tmpBalanceAfter;
            _tmpBalanceAfter = _cursor.getDouble(_cursorIndexOfBalanceAfter);
            _item = new CheckIn(_tmpId,_tmpDate,_tmpDidExercise,_tmpBalanceAfter);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getCheckInForDate(final long epochDay,
      final Continuation<? super CheckIn> $completion) {
    final String _sql = "SELECT * FROM check_ins WHERE date = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, epochDay);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<CheckIn>() {
      @Override
      @Nullable
      public CheckIn call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfDidExercise = CursorUtil.getColumnIndexOrThrow(_cursor, "didExercise");
          final int _cursorIndexOfBalanceAfter = CursorUtil.getColumnIndexOrThrow(_cursor, "balanceAfter");
          final CheckIn _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final LocalDate _tmpDate;
            final Long _tmp;
            if (_cursor.isNull(_cursorIndexOfDate)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(_cursorIndexOfDate);
            }
            final LocalDate _tmp_1 = __converters.toLocalDate(_tmp);
            if (_tmp_1 == null) {
              throw new IllegalStateException("Expected NON-NULL 'java.time.LocalDate', but it was NULL.");
            } else {
              _tmpDate = _tmp_1;
            }
            final boolean _tmpDidExercise;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfDidExercise);
            _tmpDidExercise = _tmp_2 != 0;
            final double _tmpBalanceAfter;
            _tmpBalanceAfter = _cursor.getDouble(_cursorIndexOfBalanceAfter);
            _result = new CheckIn(_tmpId,_tmpDate,_tmpDidExercise,_tmpBalanceAfter);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getLatestCheckIn(final Continuation<? super CheckIn> $completion) {
    final String _sql = "SELECT * FROM check_ins ORDER BY date DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<CheckIn>() {
      @Override
      @Nullable
      public CheckIn call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfDidExercise = CursorUtil.getColumnIndexOrThrow(_cursor, "didExercise");
          final int _cursorIndexOfBalanceAfter = CursorUtil.getColumnIndexOrThrow(_cursor, "balanceAfter");
          final CheckIn _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final LocalDate _tmpDate;
            final Long _tmp;
            if (_cursor.isNull(_cursorIndexOfDate)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(_cursorIndexOfDate);
            }
            final LocalDate _tmp_1 = __converters.toLocalDate(_tmp);
            if (_tmp_1 == null) {
              throw new IllegalStateException("Expected NON-NULL 'java.time.LocalDate', but it was NULL.");
            } else {
              _tmpDate = _tmp_1;
            }
            final boolean _tmpDidExercise;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfDidExercise);
            _tmpDidExercise = _tmp_2 != 0;
            final double _tmpBalanceAfter;
            _tmpBalanceAfter = _cursor.getDouble(_cursorIndexOfBalanceAfter);
            _result = new CheckIn(_tmpId,_tmpDate,_tmpDidExercise,_tmpBalanceAfter);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<CheckIn> getLatestCheckInFlow() {
    final String _sql = "SELECT * FROM check_ins ORDER BY date DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"check_ins"}, new Callable<CheckIn>() {
      @Override
      @Nullable
      public CheckIn call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfDidExercise = CursorUtil.getColumnIndexOrThrow(_cursor, "didExercise");
          final int _cursorIndexOfBalanceAfter = CursorUtil.getColumnIndexOrThrow(_cursor, "balanceAfter");
          final CheckIn _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final LocalDate _tmpDate;
            final Long _tmp;
            if (_cursor.isNull(_cursorIndexOfDate)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(_cursorIndexOfDate);
            }
            final LocalDate _tmp_1 = __converters.toLocalDate(_tmp);
            if (_tmp_1 == null) {
              throw new IllegalStateException("Expected NON-NULL 'java.time.LocalDate', but it was NULL.");
            } else {
              _tmpDate = _tmp_1;
            }
            final boolean _tmpDidExercise;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfDidExercise);
            _tmpDidExercise = _tmp_2 != 0;
            final double _tmpBalanceAfter;
            _tmpBalanceAfter = _cursor.getDouble(_cursorIndexOfBalanceAfter);
            _result = new CheckIn(_tmpId,_tmpDate,_tmpDidExercise,_tmpBalanceAfter);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<CheckIn> getTodayCheckInFlow(final long todayEpochDay) {
    final String _sql = "SELECT * FROM check_ins WHERE date = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, todayEpochDay);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"check_ins"}, new Callable<CheckIn>() {
      @Override
      @Nullable
      public CheckIn call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfDidExercise = CursorUtil.getColumnIndexOrThrow(_cursor, "didExercise");
          final int _cursorIndexOfBalanceAfter = CursorUtil.getColumnIndexOrThrow(_cursor, "balanceAfter");
          final CheckIn _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final LocalDate _tmpDate;
            final Long _tmp;
            if (_cursor.isNull(_cursorIndexOfDate)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(_cursorIndexOfDate);
            }
            final LocalDate _tmp_1 = __converters.toLocalDate(_tmp);
            if (_tmp_1 == null) {
              throw new IllegalStateException("Expected NON-NULL 'java.time.LocalDate', but it was NULL.");
            } else {
              _tmpDate = _tmp_1;
            }
            final boolean _tmpDidExercise;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfDidExercise);
            _tmpDidExercise = _tmp_2 != 0;
            final double _tmpBalanceAfter;
            _tmpBalanceAfter = _cursor.getDouble(_cursorIndexOfBalanceAfter);
            _result = new CheckIn(_tmpId,_tmpDate,_tmpDidExercise,_tmpBalanceAfter);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getTotalWorkoutCount(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM check_ins WHERE didExercise = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getCheckInBefore(final long epochDay,
      final Continuation<? super CheckIn> $completion) {
    final String _sql = "SELECT * FROM check_ins WHERE date < ? ORDER BY date DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, epochDay);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<CheckIn>() {
      @Override
      @Nullable
      public CheckIn call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfDidExercise = CursorUtil.getColumnIndexOrThrow(_cursor, "didExercise");
          final int _cursorIndexOfBalanceAfter = CursorUtil.getColumnIndexOrThrow(_cursor, "balanceAfter");
          final CheckIn _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final LocalDate _tmpDate;
            final Long _tmp;
            if (_cursor.isNull(_cursorIndexOfDate)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(_cursorIndexOfDate);
            }
            final LocalDate _tmp_1 = __converters.toLocalDate(_tmp);
            if (_tmp_1 == null) {
              throw new IllegalStateException("Expected NON-NULL 'java.time.LocalDate', but it was NULL.");
            } else {
              _tmpDate = _tmp_1;
            }
            final boolean _tmpDidExercise;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfDidExercise);
            _tmpDidExercise = _tmp_2 != 0;
            final double _tmpBalanceAfter;
            _tmpBalanceAfter = _cursor.getDouble(_cursorIndexOfBalanceAfter);
            _result = new CheckIn(_tmpId,_tmpDate,_tmpDidExercise,_tmpBalanceAfter);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
