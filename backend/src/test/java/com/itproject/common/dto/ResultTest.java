package com.itproject.common.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @Test
    void testDefaultConstructor() {
        Result<String> result = new Result<>();
        
        assertFalse(result.isSuccess());
        assertNull(result.getMessage());
        assertNull(result.getData());
        assertNull(result.getErrorCode());
        assertTrue(result.getTimestamp() > 0);
    }

    @Test
    void testSuccessWithData() {
        String testData = "test data";
        Result<String> result = Result.success(testData);
        
        assertTrue(result.isSuccess());
        assertEquals(testData, result.getData());
        assertEquals("Success", result.getMessage());
        assertNull(result.getErrorCode());
        assertTrue(result.getTimestamp() > 0);
    }

    @Test
    void testSuccessWithDataAndMessage() {
        String testData = "test data";
        String customMessage = "Custom success message";
        Result<String> result = Result.success(testData, customMessage);
        
        assertTrue(result.isSuccess());
        assertEquals(testData, result.getData());
        assertEquals(customMessage, result.getMessage());
        assertNull(result.getErrorCode());
        assertTrue(result.getTimestamp() > 0);
    }

    @Test
    void testSuccessWithNullData() {
        Result<String> result = Result.success(null);
        
        assertTrue(result.isSuccess());
        assertNull(result.getData());
        assertEquals("Success", result.getMessage());
        assertNull(result.getErrorCode());
        assertTrue(result.getTimestamp() > 0);
    }

    @Test
    void testErrorWithMessage() {
        String errorMessage = "Something went wrong";
        Result<String> result = Result.error(errorMessage);
        
        assertFalse(result.isSuccess());
        assertNull(result.getData());
        assertEquals(errorMessage, result.getMessage());
        assertNull(result.getErrorCode());
        assertTrue(result.getTimestamp() > 0);
    }

    @Test
    void testErrorWithMessageAndCode() {
        String errorMessage = "Validation failed";
        String errorCode = "VALIDATION_ERROR";
        Result<String> result = Result.error(errorMessage, errorCode);
        
        assertFalse(result.isSuccess());
        assertNull(result.getData());
        assertEquals(errorMessage, result.getMessage());
        assertEquals(errorCode, result.getErrorCode());
        assertTrue(result.getTimestamp() > 0);
    }

    @Test
    void testErrorWithNullMessage() {
        Result<String> result = Result.error(null);
        
        assertFalse(result.isSuccess());
        assertNull(result.getData());
        assertNull(result.getMessage());
        assertNull(result.getErrorCode());
        assertTrue(result.getTimestamp() > 0);
    }

    @Test
    void testErrorWithNullMessageAndCode() {
        Result<String> result = Result.error(null, null);
        
        assertFalse(result.isSuccess());
        assertNull(result.getData());
        assertNull(result.getMessage());
        assertNull(result.getErrorCode());
        assertTrue(result.getTimestamp() > 0);
    }

    @Test
    void testSuccessWithComplexObject() {
        TestObject testObj = new TestObject("test", 123);
        Result<TestObject> result = Result.success(testObj);
        
        assertTrue(result.isSuccess());
        assertEquals(testObj, result.getData());
        assertEquals("Success", result.getMessage());
        assertNull(result.getErrorCode());
        assertTrue(result.getTimestamp() > 0);
    }

    @Test
    void testSettersAndGetters() {
        Result<String> result = new Result<>();
        
        // Test setters
        result.setSuccess(true);
        result.setMessage("Test message");
        result.setData("Test data");
        result.setErrorCode("TEST_ERROR");
        result.setTimestamp(1234567890L);
        
        // Test getters
        assertTrue(result.isSuccess());
        assertEquals("Test message", result.getMessage());
        assertEquals("Test data", result.getData());
        assertEquals("TEST_ERROR", result.getErrorCode());
        assertEquals(1234567890L, result.getTimestamp());
    }

    @Test
    void testTimestampIsSetOnCreation() {
        long beforeCreation = System.currentTimeMillis();
        Result<String> result = new Result<>();
        long afterCreation = System.currentTimeMillis();
        
        assertTrue(result.getTimestamp() >= beforeCreation);
        assertTrue(result.getTimestamp() <= afterCreation);
    }

    @Test
    void testMultipleSuccessCallsHaveDifferentTimestamps() throws InterruptedException {
        Result<String> result1 = Result.success("data1");
        Thread.sleep(1); // Ensure different timestamps
        Result<String> result2 = Result.success("data2");
        
        assertTrue(result2.getTimestamp() > result1.getTimestamp());
    }

    @Test
    void testMultipleErrorCallsHaveDifferentTimestamps() throws InterruptedException {
        Result<String> result1 = Result.error("error1");
        Thread.sleep(1); // Ensure different timestamps
        Result<String> result2 = Result.error("error2");
        
        assertTrue(result2.getTimestamp() > result1.getTimestamp());
    }

    // Helper class for testing complex objects
    private static class TestObject {
        private String name;
        private int value;
        
        public TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            TestObject that = (TestObject) obj;
            return value == that.value && java.util.Objects.equals(name, that.name);
        }
        
        @Override
        public int hashCode() {
            return java.util.Objects.hash(name, value);
        }
    }
}
