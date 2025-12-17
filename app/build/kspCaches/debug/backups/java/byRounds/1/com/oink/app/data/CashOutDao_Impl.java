package com.oink.app.data;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Double;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class CashOutDao_Impl implements CashOutDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<CashOut> __insertionAdapterOfCashOut;

  public CashOutDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfCashOut = new EntityInsertionAdapter<CashOut>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `cash_outs` (`id`,`name`,`amount`,`emoji`,`cashedOutAt`,`balanceBefore`,`balanceAfter`,`exerciseRewardAtTime`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CashOut entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindDouble(3, entity.getAmount());
        statement.bindString(4, entity.getEmoji());
        statement.bindLong(5, entity.getCashedOutAt());
        statement.bindDouble(6, entity.getBalanceBefore());
        statement.bindDouble(7, entity.getBalanceAfter());
        statement.bindDouble(8, entity.getExerciseRewardAtTime());
      }
    };
  }

  @Override
  public Object insert(final CashOut cashOut, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfCashOut.insertAndReturnId(cashOut);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<CashOut>> getAllCashOutsFlow() {
    final String _sql = "SELECT * FROM cash_outs ORDER BY cashedOutAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"cash_outs"}, new Callable<List<CashOut>>() {
      @Override
      @NonNull
      public List<CashOut> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfEmoji = CursorUtil.getColumnIndexOrThrow(_cursor, "emoji");
          final int _cursorIndexOfCashedOutAt = CursorUtil.getColumnIndexOrThrow(_cursor, "cashedOutAt");
          final int _cursorIndexOfBalanceBefore = CursorUtil.getColumnIndexOrThrow(_cursor, "balanceBefore");
          final int _cursorIndexOfBalanceAfter = CursorUtil.getColumnIndexOrThrow(_cursor, "balanceAfter");
          final int _cursorIndexOfExerciseRewardAtTime = CursorUtil.getColumnIndexOrThrow(_cursor, "exerciseRewardAtTime");
          final List<CashOut> _result = new ArrayList<CashOut>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CashOut _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final String _tmpEmoji;
            _tmpEmoji = _cursor.getString(_cursorIndexOfEmoji);
            final long _tmpCashedOutAt;
            _tmpCashedOutAt = _cursor.getLong(_cursorIndexOfCashedOutAt);
            final double _tmpBalanceBefore;
            _tmpBalanceBefore = _cursor.getDouble(_cursorIndexOfBalanceBefore);
            final double _tmpBalanceAfter;
            _tmpBalanceAfter = _cursor.getDouble(_cursorIndexOfBalanceAfter);
            final double _tmpExerciseRewardAtTime;
            _tmpExerciseRewardAtTime = _cursor.getDouble(_cursorIndexOfExerciseRewardAtTime);
            _item = new CashOut(_tmpId,_tmpName,_tmpAmount,_tmpEmoji,_tmpCashedOutAt,_tmpBalanceBefore,_tmpBalanceAfter,_tmpExerciseRewardAtTime);
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
  public Object getAllCashOuts(final Continuation<? super List<CashOut>> $completion) {
    final String _sql = "SELECT * FROM cash_outs ORDER BY cashedOutAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<CashOut>>() {
      @Override
      @NonNull
      public List<CashOut> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfEmoji = CursorUtil.getColumnIndexOrThrow(_cursor, "emoji");
          final int _cursorIndexOfCashedOutAt = CursorUtil.getColumnIndexOrThrow(_cursor, "cashedOutAt");
          final int _cursorIndexOfBalanceBefore = CursorUtil.getColumnIndexOrThrow(_cursor, "balanceBefore");
          final int _cursorIndexOfBalanceAfter = CursorUtil.getColumnIndexOrThrow(_cursor, "balanceAfter");
          final int _cursorIndexOfExerciseRewardAtTime = CursorUtil.getColumnIndexOrThrow(_cursor, "exerciseRewardAtTime");
          final List<CashOut> _result = new ArrayList<CashOut>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CashOut _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final String _tmpEmoji;
            _tmpEmoji = _cursor.getString(_cursorIndexOfEmoji);
            final long _tmpCashedOutAt;
            _tmpCashedOutAt = _cursor.getLong(_cursorIndexOfCashedOutAt);
            final double _tmpBalanceBefore;
            _tmpBalanceBefore = _cursor.getDouble(_cursorIndexOfBalanceBefore);
            final double _tmpBalanceAfter;
            _tmpBalanceAfter = _cursor.getDouble(_cursorIndexOfBalanceAfter);
            final double _tmpExerciseRewardAtTime;
            _tmpExerciseRewardAtTime = _cursor.getDouble(_cursorIndexOfExerciseRewardAtTime);
            _item = new CashOut(_tmpId,_tmpName,_tmpAmount,_tmpEmoji,_tmpCashedOutAt,_tmpBalanceBefore,_tmpBalanceAfter,_tmpExerciseRewardAtTime);
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
  public Object getTotalCashedOut(final Continuation<? super Double> $completion) {
    final String _sql = "SELECT COALESCE(SUM(amount), 0.0) FROM cash_outs";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Double>() {
      @Override
      @NonNull
      public Double call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Double _result;
          if (_cursor.moveToFirst()) {
            final double _tmp;
            _tmp = _cursor.getDouble(0);
            _result = _tmp;
          } else {
            _result = 0.0;
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
  public Flow<Double> getTotalCashedOutFlow() {
    final String _sql = "SELECT COALESCE(SUM(amount), 0.0) FROM cash_outs";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"cash_outs"}, new Callable<Double>() {
      @Override
      @NonNull
      public Double call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Double _result;
          if (_cursor.moveToFirst()) {
            final double _tmp;
            _tmp = _cursor.getDouble(0);
            _result = _tmp;
          } else {
            _result = 0.0;
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
  public Object getCashOutCount(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM cash_outs";
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
  public Object getMostRecentCashOut(final Continuation<? super CashOut> $completion) {
    final String _sql = "SELECT * FROM cash_outs ORDER BY cashedOutAt DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<CashOut>() {
      @Override
      @Nullable
      public CashOut call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfEmoji = CursorUtil.getColumnIndexOrThrow(_cursor, "emoji");
          final int _cursorIndexOfCashedOutAt = CursorUtil.getColumnIndexOrThrow(_cursor, "cashedOutAt");
          final int _cursorIndexOfBalanceBefore = CursorUtil.getColumnIndexOrThrow(_cursor, "balanceBefore");
          final int _cursorIndexOfBalanceAfter = CursorUtil.getColumnIndexOrThrow(_cursor, "balanceAfter");
          final int _cursorIndexOfExerciseRewardAtTime = CursorUtil.getColumnIndexOrThrow(_cursor, "exerciseRewardAtTime");
          final CashOut _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final String _tmpEmoji;
            _tmpEmoji = _cursor.getString(_cursorIndexOfEmoji);
            final long _tmpCashedOutAt;
            _tmpCashedOutAt = _cursor.getLong(_cursorIndexOfCashedOutAt);
            final double _tmpBalanceBefore;
            _tmpBalanceBefore = _cursor.getDouble(_cursorIndexOfBalanceBefore);
            final double _tmpBalanceAfter;
            _tmpBalanceAfter = _cursor.getDouble(_cursorIndexOfBalanceAfter);
            final double _tmpExerciseRewardAtTime;
            _tmpExerciseRewardAtTime = _cursor.getDouble(_cursorIndexOfExerciseRewardAtTime);
            _result = new CashOut(_tmpId,_tmpName,_tmpAmount,_tmpEmoji,_tmpCashedOutAt,_tmpBalanceBefore,_tmpBalanceAfter,_tmpExerciseRewardAtTime);
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
