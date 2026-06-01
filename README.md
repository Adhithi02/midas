# Midas Core

Midas Core is the backend for the **Midas** project in the JPMorgan Chase **Advanced Software Engineering Forage** program. It ingests financial transactions from Kafka, validates and persists them, applies transaction incentives from a separate service, and exposes user balances over HTTP.

The system is built as a single Spring Boot application that combines event-driven processing (Kafka) with relational storage (H2 via JPA) and REST APIs.

## What This Project Does

1. **Consumes transactions** from a Kafka topic (`trader-updates`).
2. **Validates** each transaction (valid sender/recipient, sufficient sender balance).
3. **Records** valid transactions in an H2 database and updates user balances.
4. **Calls the Incentive API** for each valid transaction and credits any incentive to the recipient (not deducted from the sender).
5. **Serves balances** via a REST endpoint so users can query their current balance by `userId`.

## Architecture Overview

```
                    ┌─────────────────────────┐
                    │  Transaction Incentive  │
                    │  API (port 8080)        │
                    │  POST /incentive        │
                    └───────────┬─────────────┘
                                │
┌──────────────┐    Kafka       │     ┌──────────────────────────────┐
│  Producers   │ ────────────── ┼──►  │         Midas Core           │
│  (tests /    │  trader-updates│     │  • TransactionListener       │
│   external)  │                │     │  • DatabaseConduit           │
└──────────────┘                │     │  • IncentiveApiClient        │
                                │     │  • BalanceController         │
                                │     │  • H2 + JPA                  │
                                └─────┤  REST GET /balance :33400    │
                                      └──────────────────────────────┘
```

## Tech Stack

- **Java 17**
- **Spring Boot 3.2** (Web, Data JPA, Kafka)
- **Apache Kafka** (consumer; embedded broker in tests)
- **H2** (in-memory database for local development)
- **Maven**

## Implementation Summary (By Task)

### Task 1 — Application bootstrap

- Added Spring Boot dependencies (Web, JPA, Kafka, H2, test support).
- Configured base project structure and `MidasCoreApplication`.
- Verified the application starts successfully (`TaskOneTests`).

### Task 2 — Kafka integration

- **`TransactionListener`**: `@KafkaListener` on `${general.kafka-topic}` (`trader-updates`).
- Deserializes messages to `Transaction` (JSON) using Spring Kafka configuration in `src/main/resources/application.yml`.
- Consumer uses `JsonDeserializer` with trusted package `com.jpmc.midascore.foundation`.

### Task 3 — Database persistence (H2 + JPA)

- **`UserRecord`**: JPA entity for users (name, balance).
- **`TransactionRecord`**: JPA entity with `@ManyToOne` links to sender and recipient `UserRecord` (separate from the Kafka `Transaction` DTO).
- **`DatabaseConduit.processTransaction()`**:
  - Discards invalid transactions (unknown user, insufficient funds).
  - Debits sender, credits recipient, saves `TransactionRecord`.
- **`TransactionListener`** delegates each message to `DatabaseConduit`.

### Task 4 — Incentive API integration

- **`IncentiveApiClient`**: `RestTemplate` `POST` to `http://localhost:8080/incentive` with a `Transaction` body.
- **`Incentive`**: DTO for the API response (`amount`).
- After validation, fetches incentive and:
  - Stores `incentive` on `TransactionRecord`.
  - Credits recipient with `amount + incentive` (sender debited by `amount` only).
- External service JAR: `services/transaction-incentive-api.jar` (run separately on port **8080**).

### Task 5 — Balance query API

- **`BalanceController`**: `GET /balance?userId={id}` returns JSON `Balance` (`amount`).
- Returns balance `0` if the user does not exist.
- Application listens on port **33400** (`server.port` in `application.yml`).

## Key Components

| Component | Role |
|-----------|------|
| `TransactionListener` | Kafka consumer entry point |
| `DatabaseConduit` | Validation, balance updates, persistence |
| `IncentiveApiClient` | HTTP client for incentive service |
| `BalanceController` | REST balance queries |
| `UserRecord` / `TransactionRecord` | JPA entities |
| `Transaction` / `Balance` / `Incentive` | API and messaging DTOs |

## Configuration

- **Kafka topic**: `general.kafka-topic` → `trader-updates` (`application.yml`)
- **Midas Core HTTP port**: `33400`
- **Incentive API**: `8080` (separate process)

## Running Locally

### 1. Start the Incentive API (required for Tasks 4–5)

```bash
java -jar services/transaction-incentive-api.jar
```

### 2. Run Midas Core

```bash
./mvnw spring-boot:run
```

Or on Windows:

```bash
mvnw.cmd spring-boot:run
```

### 3. Query a balance

```bash
curl "http://localhost:33400/balance?userId=1"
```

## Running Tests

Tests use an embedded Kafka broker. For `TaskFourTests` and `TaskFiveTests`, start the incentive API first.

```bash
mvn test -Dtest=TaskOneTests
mvn test -Dtest=TaskTwoTests
mvn test -Dtest=TaskThreeTests
mvn test -Dtest=TaskFourTests
mvn test -Dtest=TaskFiveTests
```

Some verifier tests (`TaskTwoTests`–`TaskFourTests`) loop until you stop them after reading debugger output.

## Project Structure

```
midas/
├── services/
│   └── transaction-incentive-api.jar   # External incentive service
├── src/main/java/com/jpmc/midascore/
│   ├── component/                      # Kafka, DB, incentive client
│   ├── controller/                     # Balance REST API
│   ├── entity/                         # JPA entities
│   ├── foundation/                     # Shared DTOs
│   └── repository/                     # Spring Data repositories
├── src/main/resources/application.yml
└── src/test/                           # Task verifier tests + test data
```

## Notes

- H2 is in-memory and suitable for development; JPA abstracts the database for future production use.
- The incentive service is intentionally decoupled so its rules can change without modifying Midas Core, as long as the REST contract stays the same.

## Acknowledgments

Part of the [JPMC Advanced Software Engineering Forage](https://www.theforage.com/) curriculum. Upstream template: `vagabond-systems/forage-midas`.
