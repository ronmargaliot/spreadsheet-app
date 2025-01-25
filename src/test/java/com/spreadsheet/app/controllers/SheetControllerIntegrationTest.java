package com.spreadsheet.app.controllers;

import com.spreadsheet.app.AppApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests using a running server instance (RANDOM_PORT).
 * These tests verify end-to-end HTTP behavior and JSON handling.
 */
@SpringBootTest(
        classes = AppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
class SheetControllerIntegrationTest {

    @LocalServerPort
    int port;

    /**
     * Tests creating a sheet, setting a cell,
     * then trying an invalid type lookup that should fail.
     */
    @Test
    void testCreateSheetAndSetLookup() {
        RestTemplate restTemplate = new RestTemplate();

        // 1) Create a new sheet
        String createUrl = "http://localhost:" + port + "/sheet";
        String body = "{\n" +
                "  \"columns\": [\n" +
                "    {\"name\": \"A\", \"type\": \"STRING\"},\n" +
                "    {\"name\": \"B\", \"type\": \"BOOLEAN\"}\n" +
                "  ]\n" +
                "}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(body, headers);

        ResponseEntity<Long> createResponse =
                restTemplate.postForEntity(createUrl, request, Long.class);
        assertEquals(HttpStatus.OK, createResponse.getStatusCode());
        Long sheetId = createResponse.getBody();
        assertNotNull(sheetId);

        // 2) Set cell (A,10) to "hello"
        String setCellUrl = "http://localhost:" + port + "/sheet/" + sheetId + "/cell/A/10";
        HttpHeaders cellHeaders = new HttpHeaders();
        cellHeaders.setContentType(MediaType.TEXT_PLAIN);
        HttpEntity<String> helloRequest = new HttpEntity<>("hello", cellHeaders);
        restTemplate.exchange(setCellUrl, HttpMethod.PUT, helloRequest, Void.class);

        // 3) Attempt to set (B,1) => "lookup(A,10)" => mismatch => expect error
        String setCellUrlB1 = "http://localhost:" + port + "/sheet/" + sheetId + "/cell/B/1";
        HttpEntity<String> badLookupRequest = new HttpEntity<>("lookup(A,10)", cellHeaders);
        try {
            restTemplate.exchange(setCellUrlB1, HttpMethod.PUT, badLookupRequest, Void.class);
            fail("Should have thrown an error due to type mismatch!");
        } catch (Exception e) {
            // Expected exception
        }

        // DOUBLE in BOOLEAN column
        HttpEntity<String> doubleRequest = new HttpEntity<>("42.5", cellHeaders);
        try {
            restTemplate.exchange(setCellUrlB1, HttpMethod.PUT, doubleRequest, Void.class);
            fail("Should have thrown an error due to stricter type mismatch!");
        } catch (Exception e) {
            // Expected exception
        }

        // INT in BOOLEAN column
        HttpEntity<String> intRequest = new HttpEntity<>("42", cellHeaders);
        try {
            restTemplate.exchange(setCellUrlB1, HttpMethod.PUT, intRequest, Void.class);
            fail("Should have thrown an error due to stricter type mismatch!");
        } catch (Exception e) {
            // Expected exception
        }


        // 4) Confirm that "B,1" was not set
        String getSheetUrl = "http://localhost:" + port + "/sheet/" + sheetId;
        ResponseEntity<Map> getResponse = restTemplate.getForEntity(getSheetUrl, Map.class);
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertEquals("hello", getResponse.getBody().get("A,10"));
        assertFalse(getResponse.getBody().containsKey("B,1"));
    }

    @Test
    void testGetForwardDependencyGraph() {
        RestTemplate restTemplate = new RestTemplate();

        // 1) Create sheet
        String createUrl = "http://localhost:" + port + "/sheet";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\n" +
                "  \"columns\": [\n" +
                "    {\"name\": \"A\", \"type\": \"STRING\"},\n" +
                "    {\"name\": \"B\", \"type\": \"STRING\"},\n" +
                "    {\"name\": \"C\", \"type\": \"STRING\"}\n" +
                "  ]\n" +
                "}";
        HttpEntity<String> createRequest = new HttpEntity<>(body, headers);
        ResponseEntity<Long> createResponse = restTemplate.postForEntity(createUrl, createRequest, Long.class);
        long sheetId = createResponse.getBody();
        assertNotNull(sheetId);

        // 2) Set references: (B,1)->lookup(C,2), (A,1)->lookup(B,1)
        restTemplate.put("http://localhost:" + port + "/sheet/" + sheetId + "/cell/B/1",
                new HttpEntity<>("lookup(C,2)", headers));
        restTemplate.put("http://localhost:" + port + "/sheet/" + sheetId + "/cell/A/1",
                new HttpEntity<>("lookup(B,1)", headers));

        // 3) GET forward dependencies
        String fwdUrl = "http://localhost:" + port + "/sheet/" + sheetId + "/forwardDependencies";
        ResponseEntity<Map> response = restTemplate.getForEntity(fwdUrl, Map.class);
        Map<String, List<String>> forwardGraph = response.getBody();
        assertNotNull(forwardGraph);

        // "1:B" -> ["2:C"], "1:A" -> ["1:B"], "2:C" -> []
        assertEquals(Collections.singletonList("2:C"), forwardGraph.get("1:B"));
        assertEquals(Collections.singletonList("1:B"), forwardGraph.get("1:A"));
        assertTrue(forwardGraph.get("2:C").isEmpty());
    }

    @Test
    void testGetReverseDependencyGraph() {
        RestTemplate restTemplate = new RestTemplate();

        // 1) Create sheet
        String createUrl = "http://localhost:" + port + "/sheet";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\n" +
                "  \"columns\": [\n" +
                "    {\"name\": \"A\", \"type\": \"STRING\"},\n" +
                "    {\"name\": \"B\", \"type\": \"STRING\"},\n" +
                "    {\"name\": \"C\", \"type\": \"STRING\"}\n" +
                "  ]\n" +
                "}";
        HttpEntity<String> createRequest = new HttpEntity<>(body, headers);
        ResponseEntity<Long> createResponse = restTemplate.postForEntity(createUrl, createRequest, Long.class);
        long sheetId = createResponse.getBody();
        assertNotNull(sheetId);

        // 2) Set references: (B,10)->lookup(A,10), (C,10)->lookup(B,10)
        restTemplate.put("http://localhost:" + port + "/sheet/" + sheetId + "/cell/B/10",
                new HttpEntity<>("lookup(A,10)", headers));
        restTemplate.put("http://localhost:" + port + "/sheet/" + sheetId + "/cell/C/10",
                new HttpEntity<>("lookup(B,10)", headers));

        // 3) GET reverse dependencies
        String revUrl = "http://localhost:" + port + "/sheet/" + sheetId + "/reverseDependencies";
        ResponseEntity<Map> response = restTemplate.getForEntity(revUrl, Map.class);
        Map<String, List<String>> reverseGraph = response.getBody();
        assertNotNull(reverseGraph);

        // forward was:
        //   "10:B" -> ["10:A"]
        //   "10:C" -> ["10:B"]
        // So reverse should be:
        //   "10:A" -> ["10:B"]
        //   "10:B" -> ["10:C"]
        //   "10:C" -> []
        assertEquals(Collections.singletonList("10:B"), reverseGraph.get("10:A"));
        assertEquals(Collections.singletonList("10:C"), reverseGraph.get("10:B"));
        assertTrue(reverseGraph.get("10:C").isEmpty());
    }


    /**
     * Verifies partial re-evaluation:
     * (C,1)-> lookup(A,1). Changing (A,1) re-updates (C,1).
     */
    @Test
    void testPartialReEvaluationIntegration() {
        RestTemplate restTemplate = new RestTemplate();

        // 1) Create a new sheet with (A,B,C) all STRING
        String createUrl = "http://localhost:" + port + "/sheet";
        String createBody = "{\n" +
                "  \"columns\": [\n" +
                "    {\"name\": \"A\", \"type\": \"STRING\"},\n" +
                "    {\"name\": \"B\", \"type\": \"STRING\"},\n" +
                "    {\"name\": \"C\", \"type\": \"STRING\"}\n" +
                "  ]\n" +
                "}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> createRequest = new HttpEntity<>(createBody, headers);
        ResponseEntity<Long> createResponse = restTemplate.postForEntity(createUrl, createRequest, Long.class);
        long sheetId = createResponse.getBody();
        assertNotNull(sheetId);

        // 2) (A,1) => "hello"
        String a1Url = "http://localhost:" + port + "/sheet/" + sheetId + "/cell/A/1";
        restTemplate.put(a1Url, new HttpEntity<>("hello", headers));

        // 3) (C,1) => "lookup(A,1)"
        String c1Url = "http://localhost:" + port + "/sheet/" + sheetId + "/cell/C/1";
        restTemplate.put(c1Url, new HttpEntity<>("lookup(A,1)", headers));

        // 4) GET => "C,1" == "hello"
        String getUrl = "http://localhost:" + port + "/sheet/" + sheetId;
        ResponseEntity<Map> response1 = restTemplate.getForEntity(getUrl, Map.class);
        Map<String, Object> data1 = response1.getBody();
        assertEquals("hello", data1.get("C,1"));

        // 5) Change (A,1) => "world"
        restTemplate.put(a1Url, new HttpEntity<>("world", headers));

        // 6) GET => "C,1" == "world"
        ResponseEntity<Map> response2 = restTemplate.getForEntity(getUrl, Map.class);
        Map<String, Object> data2 = response2.getBody();
        assertEquals("world", data2.get("C,1"));
    }

    /**
     * Tests a single-cell cycle:
     * (A,10)-> "lookup(A,10)" => should fail and revert.
     */
    @Test
    void testSingleCellCycleIntegration() {
        RestTemplate restTemplate = new RestTemplate();

        // 1) Create sheet
        String createUrl = "http://localhost:" + port + "/sheet";
        String createBody = "{\n" +
                "  \"columns\": [\n" +
                "    {\"name\": \"A\", \"type\": \"STRING\"},\n" +
                "    {\"name\": \"B\", \"type\": \"STRING\"}\n" +
                "  ]\n" +
                "}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> createRequest = new HttpEntity<>(createBody, headers);
        ResponseEntity<Long> createResponse = restTemplate.postForEntity(createUrl, createRequest, Long.class);
        long sheetId = createResponse.getBody();
        assertNotNull(sheetId);

        // 2) (A,10) => "hello"
        String a10Url = "http://localhost:" + port + "/sheet/" + sheetId + "/cell/A/10";
        restTemplate.put(a10Url, new HttpEntity<>("hello", headers));

        // 3) (A,10)=> "lookup(A,10)" => expect cycle error
        try {
            restTemplate.put(a10Url, new HttpEntity<>("lookup(A,10)", headers));
            fail("Expected a circular reference exception from the server!");
        } catch (Exception ex) {
            // success
        }

        // 4) Confirm old value remains
        String getUrl = "http://localhost:" + port + "/sheet/" + sheetId;
        ResponseEntity<Map> getResponse = restTemplate.getForEntity(getUrl, Map.class);
        assertEquals("hello", getResponse.getBody().get("A,10"));
    }
}
