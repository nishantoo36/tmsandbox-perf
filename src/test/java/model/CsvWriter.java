package model;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * CsvWriter
 * <p>
 * Thread-safe, singleton-per-path CSV writer used by all Gatling virtual
 * users to append {@link PromotionRecord} rows to a shared output file.
 *
 * <p>The header is written exactly once regardless of how many virtual users
 * call {@link #append(List)} concurrently.
 */
public final class CsvWriter {

    private static volatile CsvWriter INSTANCE;

    private final Path           filePath;
    private final ReentrantLock  lock            = new ReentrantLock();
    private final AtomicBoolean  headerWritten   = new AtomicBoolean(false);

    // ── Singleton factory ──────────────────────────────────────────────────

    public static CsvWriter getInstance(String csvOutputPath) {
        if (INSTANCE == null) {
            synchronized (CsvWriter.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CsvWriter(csvOutputPath);
                }
            }
        }
        return INSTANCE;
    }

    /** Clears the singleton (used in tests / between runs). */
    public static void reset() {
        synchronized (CsvWriter.class) {
            INSTANCE = null;
        }
    }

    // ── Constructor ────────────────────────────────────────────────────────

    private CsvWriter(String csvOutputPath) {
        this.filePath = Path.of(csvOutputPath);
        try {
            Files.createDirectories(filePath.getParent() == null
                    ? Path.of(".") : filePath.getParent());
            // Truncate / create fresh on each test run
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Cannot initialise CSV output at: " + csvOutputPath, e);
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Appends one or more {@link PromotionRecord}s to the CSV file.
     * The header row is written before the first batch of records.
     *
     * @param records records to write (must not be null or empty)
     */
    public void append(List<PromotionRecord> records) {
        if (records == null || records.isEmpty()) return;

        lock.lock();
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath.toFile(), true))) {
            if (headerWritten.compareAndSet(false, true)) {
                pw.println(PromotionRecord.CSV_HEADER);
            }
            records.forEach(r -> pw.println(r.toCsvLine()));
            pw.flush();
        } catch (IOException e) {
            System.err.println("[CsvWriter] Failed to write records: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }
}
