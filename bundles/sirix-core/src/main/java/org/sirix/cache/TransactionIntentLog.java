package org.sirix.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

import org.sirix.api.PageReadOnlyTrx;
import org.sirix.page.CASPage;
import org.sirix.page.NamePage;
import org.sirix.page.PageReference;
import org.sirix.page.PathPage;
import org.sirix.page.PathSummaryPage;
import org.sirix.page.RevisionRootPage;
import org.sirix.page.UberPage;
import org.sirix.settings.Constants;
import com.google.common.base.MoreObjects;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

/**
 * The transaction intent log, used for logging everything a write transaction changes.
 *
 * @author Johannes Lichtenberger <lichtenberger.johannes@gmail.com>
 */
public final class TransactionIntentLog implements AutoCloseable {
  /**
   * The collection to hold the maps.
   */
  private final Map<PageReference, PageContainer> mMap;

  /**
   * Maps in-memory key to persistent key and vice versa.
   */
  private final BiMap<Integer, Long> mMapToPersistentLogKey;

  /**
   * The reference to the second cache.
   */
  private final PersistentFileCache mSecondCache;

  /**
   * The log key.
   */
  private int mLogKey;

  /**
   * Creates a new LRU cache.
   *
   * @param secondCache         the reference to the second {@link Cache} where the data is stored when it
   *                            gets removed from the first one.
   * @param maxInMemoryCapacity the maximum size of the in-memory map
   */
  public TransactionIntentLog(final PersistentFileCache secondCache, final int maxInMemoryCapacity) {
    // Assertion instead of checkNotNull(...).
    assert secondCache != null;
    mLogKey = 0;
    mSecondCache = secondCache;
    mMapToPersistentLogKey = HashBiMap.create(maxInMemoryCapacity);
    mMap = new LinkedHashMap<>(maxInMemoryCapacity) {
      private static final long serialVersionUID = 1;

      @Override
      protected boolean removeEldestEntry(final @Nullable Map.Entry<PageReference, PageContainer> eldest) {
        boolean returnVal = false;
        if (size() > maxInMemoryCapacity) {
          if (eldest != null) {
            final PageReference key = eldest.getKey();
            assert key.getLogKey() != Constants.NULL_ID_INT;
            final PageContainer value = eldest.getValue();
            if (key != null && value != null) {
              mSecondCache.put(key, value);
              mMapToPersistentLogKey.put(key.getLogKey(), key.getPersistentLogKey());
            }
          }
          returnVal = true;
        }
        return returnVal;
      }
    };
//    // Assertion instead of checkNotNull(...).
//    assert secondCache != null;
//    mLogKey = 0;
//    mSecondCache = secondCache;
//    mMapToPersistentLogKey = HashBiMap.create();
//    mMap = new LinkedHashMap<>(maxInMemoryCapacity, 0.75f, true) {
//      private static final long serialVersionUID = 1;
//
//      @Override
//      protected boolean removeEldestEntry(final @Nullable Map.Entry<PageReference, PageContainer> eldest) {
//        boolean returnVal = false;
//        if (size() > maxInMemoryCapacity) {
//          if (isImportant(eldest)) {
//            this.remove(eldest.getKey());
//            this.put(eldest.getKey(), eldest.getValue());
//          } else {
//            final PageReference key = eldest.getKey();
//            assert key.getLogKey() != Constants.NULL_ID_INT;
//            final PageContainer value = eldest.getValue();
//            if (key != null && value != null) {
//              mSecondCache.put(key, value);
//              mMapToPersistentLogKey.put(key.getLogKey(), key.getPersistentLogKey());
//            }
//          }
//          returnVal = true;
//        }
//        return returnVal;
//      }
//
//      private boolean isImportant(Map.Entry<PageReference, PageContainer> eldest) {
//        final var page = eldest.getValue().getComplete();
//        if (page instanceof RevisionRootPage || page instanceof NamePage || page instanceof CASPage
//            || page instanceof PathPage || page instanceof PathSummaryPage || page instanceof UberPage) {
//          return true;
//        }
//        return false;
//      }
//    };
//        // Assertion instead of checkNotNull(...).
//        assert secondCache != null;
//        mLogKey = 0;
//        mSecondCache = secondCache;
//        mMapToPersistentLogKey = HashBiMap.create(maxInMemoryCapacity);
//        mMap = new LinkedHashMap<>(maxInMemoryCapacity, 0.75f, true) {
//          private static final long serialVersionUID = 1;
//
//          @Override
//          protected boolean removeEldestEntry(final @Nullable Map.Entry<PageReference, PageContainer> eldest) {
//            boolean returnVal = false;
//            if (size() > maxInMemoryCapacity) {
//              if (eldest != null) {
//                final var iter = mMap.entrySet().iterator();
//                while (iter.hasNext()) {
//                  final Map.Entry<PageReference, PageContainer> entry = iter.next();
//                  final PageReference key = entry.getKey();
//                  assert key.getLogKey() != Constants.NULL_ID_INT;
//                  PageContainer value = entry.getValue();
//                  if (!isImportant(entry) && key != null && value != null) {
//                    iter.remove();
//                    mSecondCache.put(key, value);
//                    value = null;
//                    mMapToPersistentLogKey.put(key.getLogKey(), key.getPersistentLogKey());
//                  }
//                }
//              }
//              returnVal = true;
//            }
//            return returnVal;
//          }
//        };
  }

  /**
   * Retrieves an entry from the cache.<br>
   * The retrieved entry becomes the MRU (most recently used) entry.
   *
   * @param key the key whose associated value is to be returned.
   * @return the value associated to this key, or {@code null} if no value with this key exists in the
   * cache
   */
  public PageContainer get(final PageReference key, final PageReadOnlyTrx pageRtx) {
    PageContainer value = mMap.get(key);
    if (value == null) {
      if (key.getLogKey() != Constants.NULL_ID_INT) {
        final Long persistentKey = mMapToPersistentLogKey.get(key.getLogKey());
        if (persistentKey != null)
          key.setPersistentLogKey(persistentKey);
      }
      value = mSecondCache.get(key, pageRtx);
      if (value != null && !PageContainer.emptyInstance().equals(value)) {
        mMapToPersistentLogKey.remove(key.getPersistentLogKey());
        key.setPersistentLogKey(Constants.NULL_ID_LONG);
        put(key, value);
      }
    }
    return value;
  }

  /**
   * Adds an entry to this cache. If the cache is full, the LRU (least recently used) entry is
   * dropped.
   *
   * @param key   the key with which the specified value is to be associated
   * @param value a value to be associated with the specified key
   */
  public void put(final PageReference key, final PageContainer value) {
    if (mMap.containsKey(key)) {
      mMap.remove(key);
    }

    key.setKey(Constants.NULL_ID_LONG);
    key.setLogKey(mLogKey++);
    key.setPersistentLogKey(Constants.NULL_ID_LONG);
    mMap.put(key, value);
  }

  /**
   * Removes an entry from this cache.
   *
   * @param key the key with which the specified value is to be associated
   */
  public void remove(final PageReference key) {
    mMap.remove(key);
    mMapToPersistentLogKey.remove(key);
  }

  /**
   * Clears the cache.
   */
  public void clear() {
    mLogKey = 0;
    mMap.clear();
  }

  /**
   * Returns the number of used entries in the cache.
   *
   * @return the number of entries currently in the cache.
   */
  public int usedEntries() {
    return mMap.size();
  }

  /**
   * Returns a {@code Collection} that contains a copy of all cache entries.
   *
   * @return a {@code Collection} with a copy of the cache content
   */
  public Collection<Map.Entry<? super PageReference, ? super PageContainer>> getAll() {
    return new ArrayList<Map.Entry<? super PageReference, ? super PageContainer>>(mMap.entrySet());
  }

  //  @Override
  //  public String toString() {
  //    return MoreObjects.toStringHelper(this).add("First Cache", mMap).add("Second Cache", mSecondCache).toString();
  //  }

  /**
   * Get a view of the underlying map.
   *
   * @return an unmodifiable view of all entries in the cache
   */
  public Map<PageReference, PageContainer> getMap() {
    return Collections.unmodifiableMap(mMap);
  }

  /**
   * Truncate the log.
   *
   * @return this log instance
   */
  public TransactionIntentLog truncate() {
    mSecondCache.close();
    mMapToPersistentLogKey.clear();
    mMap.clear();
    return this;
  }

  @Override
  public void close() {
    mMap.clear();
    mSecondCache.close();
  }
}
