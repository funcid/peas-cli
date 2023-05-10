package me.func.peas.tests.util;

import me.func.peas.Deencapsulation;
import me.func.peas.util.BigLongArray;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BigLongArrayTest {
  static {
    Deencapsulation.init();
  }

  @Test
  void testCreation() {
    var withCleaner = BigLongArray.withCleaner(12309);
    var withoutCleaner = BigLongArray.withoutCleaner(12309);

    System.gc();

    withCleaner.close();
    withoutCleaner.close();
  }

  @Test
  void testSet() {
    var withCleaner = BigLongArray.withCleaner(12309);
    var withoutCleaner = BigLongArray.withoutCleaner(12309);

    System.gc();
    System.gc();
    System.gc();
    System.gc();

    for (long i = 0L, length = withCleaner.length(); i < length; i++) {
      withCleaner.set(i, Long.MAX_VALUE);
    }

    for (long i = 0L, length = withoutCleaner.length(); i < length; i++) {
      withoutCleaner.set(i, Long.MAX_VALUE);
    }

    withCleaner.close();
    withoutCleaner.close();
  }

  @Test
  void testGet() {
    var withCleaner = BigLongArray.withCleaner(128);
    var withoutCleaner = BigLongArray.withoutCleaner(128);

    System.gc();
    System.gc();
    System.gc();

    for (long i = 0L, length = withCleaner.length(); i < length; i++) {
      withCleaner.set(i, Long.MAX_VALUE - 12309);
    }
    System.gc();
    System.gc();
    System.gc();

    for (long i = 0L, length = withCleaner.length(); i < length; i++) {
      System.gc();
      assertEquals(Long.MAX_VALUE - 12309, withCleaner.get(i));
    }

    for (long i = 0L, length = withoutCleaner.length(); i < length; i++) {
      withoutCleaner.set(i, Long.MAX_VALUE - 12309);
    }

    for (long i = 0L, length = withoutCleaner.length(); i < length; i++) {
      assertEquals(Long.MAX_VALUE - 12309, withoutCleaner.get(i));
    }

    withCleaner.close();
    withoutCleaner.close();
  }

  @Test
  void testForEach() {
    var withCleaner = BigLongArray.withCleaner(128);
    var withoutCleaner = BigLongArray.withoutCleaner(128);

    System.gc();
    System.gc();
    System.gc();

    for (long i = 0L, length = withCleaner.length(); i < length; i++) {
      withCleaner.set(i, Long.MAX_VALUE - 12309);
    }
    System.gc();
    System.gc();
    System.gc();

    withCleaner.forEach(value -> {
      System.gc();
      assertEquals(Long.MAX_VALUE - 12309, value);
    });

    for (long i = 0L, length = withoutCleaner.length(); i < length; i++) {
      withoutCleaner.set(i, Long.MAX_VALUE - 12309);
    }

    withoutCleaner.forEach(value -> assertEquals(Long.MAX_VALUE - 12309, value));

    withCleaner.close();
    withoutCleaner.close();
  }
}
