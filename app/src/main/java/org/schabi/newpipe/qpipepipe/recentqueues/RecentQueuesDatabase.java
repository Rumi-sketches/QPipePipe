package org.schabi.newpipe.qpipepipe.recentqueues;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * QPipePipe's own database, separate from upstream's {@code newpipe.db} so that upstream
 * schema versions and migrations never collide with the fork's. It is not part of the
 * settings export.
 */
@Database(entities = {RecentQueueEntity.class, RecentQueueStreamEntity.class}, version = 1)
public abstract class RecentQueuesDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "qpipepipe_recent_queues.db";

    private static volatile RecentQueuesDatabase instance;

    public abstract RecentQueueDAO recentQueueDAO();

    @NonNull
    public static RecentQueuesDatabase getInstance(@NonNull final Context context) {
        RecentQueuesDatabase result = instance;
        if (result == null) {
            synchronized (RecentQueuesDatabase.class) {
                result = instance;
                if (result == null) {
                    result = Room.databaseBuilder(context.getApplicationContext(),
                            RecentQueuesDatabase.class, DATABASE_NAME).build();
                    instance = result;
                }
            }
        }
        return result;
    }
}
