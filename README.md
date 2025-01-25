SPREADSHEET APP

This project is a simple in-memory spreadsheet service built with Spring Boot.

It demonstrates creating spreadsheet schemas, setting cell values (literal or

lookup), detecting cycles, handling type mismatches, and retrieving both data

and dependencies.

\--------------------------------------------------------------

1. REQUIREMENTS

\--------------------------------------------------------------

- Java 8+ (or newer)
- Maven 3+ (if using Maven) or Gradle if your build file is `build.gradle`.

\--------------------------------------------------------------

2\. **HOW TO START THE SERVER**

\--------------------------------------------------------------

1) Clone this repository.
1) In the project’s root folder (where pom.xml or build.gradle resides), open a

terminal/command prompt.

1) Run:

mvn spring-boot:run

(Or, in an IDE like IntelliJ, run the main class: AppApplication.)

1) By default, the server starts at http://localhost:8080

\--------------------------------------------------------------

3\. **HOW TO RUN THE TESTS**

\--------------------------------------------------------------

- Unit Tests (no server needed):

mvn test

This executes all JUnit tests, including SheetServiceTest, which tests

the business logic directly.

- Integration Tests:

These also run as part of mvn test by default.

SheetControllerIntegrationTest uses Spring Boot’s test runner to

spin up the application on a random port, then interacts via RestTemplate.

\--------------------------------------------------------------

4\. USAGE EXAMPLES

\--------------------------------------------------------------

Below are common requests using HTTP (curl-like syntax).

Substitute {sheetId} with the ID returned when you create a sheet.

\--------------------------------

(1) CREATE A SHEET

\--------------------------------

POST /sheet

Content-Type: application/json

{

"columns": [

{ "name": "A", "type": "STRING" },

{ "name": "B", "type": "BOOLEAN" },

{ "name": "C", "type": "STRING" }

]

}

Example Response Body:

123  (this is your sheetId)

\--------------------------------

(2) SET A LITERAL CELL

\--------------------------------

PUT /sheet/123/cell/A/10

Content-Type: text/plain

hello

HTTP 200 OK on success.

Similarly:

PUT /sheet/123/cell/B/11

Content-Type: text/plain

true

\--------------------------------

(3) SET A LOOKUP CELL (VALID)

\--------------------------------

PUT /sheet/123/cell/C/1

Content-Type: text/plain

lookup(A,10)

HTTP 200 if the lookup is valid (C is STRING, A is also STRING => no mismatch),

and no cycles. Now (C,1) -> "hello" because it looks up (A,10).

\--------------------------------

(4) TYPE MISMATCH EXAMPLE

\--------------------------------

PUT /sheet/123/cell/B/1

Content-Type: text/plain

lookup(A,10)

Since B is BOOLEAN and lookup(A,10) is STRING,

the server returns HTTP 400 with an error code "INVALID\_TYPE"

(e.g.

{

"code": "INVALID\_TYPE",

"message": "..."

}

).

\--------------------------------

(5) SINGLE-CELL CYCLE EXAMPLE

\--------------------------------

PUT /sheet/123/cell/C/1

Content-Type: text/plain

lookup(C,1)

This is "C references C". The server should reject

with a 400 "CIRCULAR\_REFERENCE" error.

\--------------------------------

(6) MULTI-CELL CYCLE EXAMPLE

\--------------------------------

1) PUT /sheet/123/cell/C/1 => "lookup(A,1)" (OK if both are STRING)
1) PUT /sheet/123/cell/A/1 => "lookup(B,1)" (OK if both are STRING)
1) PUT /sheet/123/cell/B/1 => "lookup(C,1)" => this forms a cycle (C->A->B->C).

The server rejects with a 400 "CIRCULAR\_REFERENCE".

\--------------------------------

(7) GET SHEET DATA

\--------------------------------

GET /sheet/123

Returns a JSON map of all resolved cell values:

{

"A,10": "hello",

"B,11": true,

"C,1": "hello"

...

}

\--------------------------------

(8) GET DEPENDENCIES

\--------------------------------

GET /sheet/123/forwardDependencies

Returns:

{

"10:A": [],     // or whichever references

"11:B": [],

"1:C": ["10:A"] // e.g., C references A

}

GET /sheet/123/reverseDependencies

Returns the reverse graph:

{

"10:A": ["1:C"], // A is referenced by C

"1:C": [],

"11:B": []

}

\--------------------------------------------------------------

END
