package com.kazumaproject.markdownhelperkeyboard.local_font;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** A test-only provider that can hold a pipe open without producing bytes. */
public final class LocalFontBlockingTestProvider extends ContentProvider {
    private static final Object LOCK = new Object();
    private static ParcelFileDescriptor heldWriter;
    private static boolean blockedOpened;
    private static final Object CURSOR_OPERATION_LOCK = new Object();
    private static volatile CountDownLatch cursorMoveStarted = new CountDownLatch(0);
    private static volatile CountDownLatch cursorRelease = new CountDownLatch(0);
    private static final AtomicInteger blockedCursorCloseCalls = new AtomicInteger();
    private static volatile CountDownLatch lateCursorQueryStarted = new CountDownLatch(0);
    private static volatile CountDownLatch lateCursorQueryRelease = new CountDownLatch(0);
    private static volatile boolean lateCursorReturned;
    private static final AtomicInteger lateCursorMoveCalls = new AtomicInteger();
    private static final AtomicInteger lateCursorCloseCalls = new AtomicInteger();

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder
    ) {
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME}
                : projection;
        MatrixCursor cursor;
        if ("blocked-cursor".equals(uri.getLastPathSegment())) {
            cursorMoveStarted = new CountDownLatch(1);
            cursorRelease = new CountDownLatch(1);
            blockedCursorCloseCalls.set(0);
            cursor = new MatrixCursor(columns) {
                @Override
                public boolean onMove(int oldPosition, int newPosition) {
                    synchronized (CURSOR_OPERATION_LOCK) {
                        cursorMoveStarted.countDown();
                        try {
                            cursorRelease.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                        return super.onMove(oldPosition, newPosition);
                    }
                }

                @Override
                public void close() {
                    blockedCursorCloseCalls.incrementAndGet();
                    synchronized (CURSOR_OPERATION_LOCK) {
                        super.close();
                    }
                }
            };
        } else if ("late-blocked-cursor".equals(uri.getLastPathSegment())) {
            lateCursorQueryStarted = new CountDownLatch(1);
            lateCursorQueryRelease = new CountDownLatch(1);
            lateCursorReturned = false;
            lateCursorMoveCalls.set(0);
            lateCursorCloseCalls.set(0);
            lateCursorQueryStarted.countDown();
            try {
                if (!lateCursorQueryRelease.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Test did not release the blocked cursor query");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Blocked cursor query was interrupted", e);
            }
            cursor = new MatrixCursor(columns) {
                @Override
                public boolean onMove(int oldPosition, int newPosition) {
                    lateCursorMoveCalls.incrementAndGet();
                    return super.onMove(oldPosition, newPosition);
                }

                @Override
                public void close() {
                    lateCursorCloseCalls.incrementAndGet();
                    super.close();
                }
            };
        } else {
            cursor = new MatrixCursor(columns);
        }
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) {
                row[i] = "blocked-font.ttf";
            }
        }
        cursor.addRow(row);
        if ("late-blocked-cursor".equals(uri.getLastPathSegment())) {
            lateCursorReturned = true;
        }
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if ("blocked".equals(uri.getLastPathSegment())) {
            try {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                synchronized (LOCK) {
                    closeQuietly(heldWriter);
                    heldWriter = pipe[1];
                    blockedOpened = true;
                }
                return pipe[0];
            } catch (IOException e) {
                FileNotFoundException failure = new FileNotFoundException(e.getMessage());
                failure.initCause(e);
                throw failure;
            }
        }
        if ("valid".equals(uri.getLastPathSegment())) {
            File font = new File("/system/fonts/DroidSansMono.ttf");
            if (!font.isFile()) throw new FileNotFoundException(font.getPath());
            return ParcelFileDescriptor.open(font, ParcelFileDescriptor.MODE_READ_ONLY);
        }
        throw new FileNotFoundException(uri.toString());
    }

    @Override
    public String getType(Uri uri) {
        return "font/ttf";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle response = new Bundle();
        if ("reset".equals(method)) {
            synchronized (LOCK) {
                closeQuietly(heldWriter);
                heldWriter = null;
                blockedOpened = false;
            }
        } else if ("opened".equals(method)) {
            synchronized (LOCK) {
                response.putBoolean("opened", blockedOpened);
            }
        } else if ("cursorMoveStarted".equals(method)) {
            response.putBoolean("started", cursorMoveStarted.getCount() == 0);
        } else if ("cursorCloseCount".equals(method)) {
            response.putInt("count", blockedCursorCloseCalls.get());
        } else if ("lateCursorQueryStarted".equals(method)) {
            response.putBoolean("started", lateCursorQueryStarted.getCount() == 0);
        } else if ("releaseLateCursorQuery".equals(method)) {
            lateCursorQueryRelease.countDown();
        } else if ("lateCursorReturned".equals(method)) {
            response.putBoolean("returned", lateCursorReturned);
        } else if ("lateCursorMoveCount".equals(method)) {
            response.putInt("count", lateCursorMoveCalls.get());
        } else if ("lateCursorCloseCount".equals(method)) {
            response.putInt("count", lateCursorCloseCalls.get());
        } else if ("releaseBlockedCursor".equals(method)) {
            cursorRelease.countDown();
        } else if ("release".equals(method)) {
            synchronized (LOCK) {
                closeQuietly(heldWriter);
                heldWriter = null;
            }
        }
        return response;
    }

    private static void closeQuietly(ParcelFileDescriptor descriptor) {
        if (descriptor == null) return;
        try {
            descriptor.close();
        } catch (IOException ignored) {
        }
    }
}
