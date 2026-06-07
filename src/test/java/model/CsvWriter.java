package model;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public final class CsvWriter {

    private static volatile CsvWriter instance;

    private final Path filePath;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicBoolean headerWritten = new AtomicBoolean(false);

    public static CsvWriter getInstance(String csvOutputPath) {
        if (instance == null) {
            synchronized (CsvWriter.class) {
                if (instance == null) {
                    instance = new CsvWriter(csvOutputPath);
                }
            }
        }
        return instance;
    }

    public static void reset() {
        synchronized (CsvWriter.class) {
            instance = null;
        }
    }

    private CsvWriter(String csvOutputPath) {
        this.filePath = Path.of(csvOutputPath);
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot initialise CSV output at: " + csvOutputPath, e);
        }
    }

    public void append(List<PromotionRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        lock.lock();
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(
                filePath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        ))) {
            if (headerWritten.compareAndSet(false, true)) {
                writer.println(PromotionRecord.CSV_HEADER);
            }
            records.forEach(record -> writer.println(record.toCsvLine()));
        } catch (IOException e) {
            System.err.println("[CsvWriter] Failed to write records: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }
}
