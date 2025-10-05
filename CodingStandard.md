# DeepFake Forensic Tool - Coding Standards & Style Guide

## 1. Guiding

The purpose of this document is to establish a unified set of coding standards to enhance the **Readability**, **Maintainability**, and collaborative efficiency of our project. Our guiding principle is: "Write code that can be easily understood by our future selves and by new team members."

This guide is a living document agreed upon by the team. All contributions must adhere to these standards.

## 2. Git Workflow Standards

A disciplined Git workflow is critical for managing the complexity of our project, which involves containerized infrastructure, a Java backend, and a React frontend.

### Branch Naming Convention
-   **Features:** `feat/<feature-description>` (e.g., `feat/chunked-file-upload`)
-   **Fixes:** `fix/<issue-description>` (e.g., `fix/metadata-parsing-error`)
-   **Chores/Refactoring:** `chore/<description>` (e.g., `chore/update-coding-standards`)

### Commit Message Convention
-   We adhere to the [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) specification.
-   **Format:** `<type>: <description>`
-   **Examples:**
    -   `feat: Implement JWT token validation in service layer`
    -   `fix: Resolve race condition in Kafka consumer`
    -   `test: Add unit tests for FileTypeValidationService`

### Pull Requests (PRs)
-   Direct pushes to the `main` branch are strictly prohibited. All changes must be introduced via PRs.
-   A PR should focus on a single, atomic task.
-   The PR description must clearly explain the "what" and the "why" of the changes and link to any relevant issues.
-   A PR must pass all CI checks and receive approval from at least **one** other team member before being merged.

### Addressing Branch Management Feedback
-   **Principle:** Branches must be **short-lived**.
-   **Process:** The lifecycle of a feature or fix branch is: Create -> Develop -> Commit -> Push -> Create PR -> Review -> **Merge -> Delete Immediately**.
-   **Rationale:** Deleting merged branches keeps the repository clean, significantly reduces the chance of merge conflicts, and provides a clear view of ongoing work.
-   **Note:** This is a **forward-looking policy**. There is no requirement to clean up previously merged branches from the repository's history.

## 3. Backend (Java & Spring Boot) Standards

We adopt the **[Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)** as our official style guide. This applies to all modules within the `backend/` directory (`upload/`, `metadata/`, etc.).

### Naming
-   **Classes:** `PascalCase` (e.g., `FileUploadController`).
-   **Methods & Variables:** `camelCase` (e.g., `analyzeFileMetadata`).
-   **Constants:** `CONSTANT_CASE` (e.g., `MAX_FILE_SIZE`).
-   **Packages:** `lowercase` (e.g., `com.forensictool.service`).

### Formatting
-   Formatting will be enforced automatically using the **Maven Checkstyle Plugin**.
-   **Indentation:** 2 spaces.
-   **Braces:** K&R style (`{` does not start on a new line).

### Documentation
-   All `public` methods and classes must have **Javadocs** explaining their purpose, parameters (`@param`), and return values (`@return`).
-   Complex business logic should be clarified with concise block or single-line comments.

### Architecture
-   Strictly follow the layered architecture defined in `README.md` (Controller, Service, Repository, DTO, Entity). Business logic must be confined to the `Service` layer.

## 4. Frontend (React & TypeScript) Standards

We adopt the **[Airbnb JavaScript Style Guide](https://github.com/airbnb/javascript)** (and its TypeScript variant) as our base style guide. This applies to all `.ts` and `.tsx` files in the `frontend/` directory.

### Naming
-   **React Components:** `PascalCase` (e.g., `FileUploadDialog.tsx`).
-   **Variables, Functions, Hooks:** `camelCase` (e.g., `useFileUploader`).

### Component Structure
-   Prefer functional components and Hooks over class components.
-   Adhere to the Single Responsibility Principle. Decompose complex components into smaller, reusable ones.

### TypeScript
-   **The `any` type is forbidden.** Provide explicit types for all variables, function parameters, and return values.
-   Use `interface` for defining the shape of public APIs and component props.
-   Use `type` for internal state or complex union/intersection types.
-   Shared type definitions should reside in `src/types/` and be imported as needed.

### API Services
-   All interactions with the backend API (`http://localhost:8082`) must be encapsulated within services in the `src/services/` directory. Do not use `fetch` or `axios` directly inside components.

## 5. Testing Standards

### Backend (JUnit 5 & JaCoCo)
-   **Location:** Test source files must mirror the package structure of the main codebase under `backend/src/test/java`.
-   **Principle:** Focus tests on the business logic within `Service` and `Util` classes. Use **Mockito** to mock repositories and other external dependencies for pure unit testing.
-   **Goal:** A target **Code Coverage** of **80%** for core business logic.

### Frontend (Vitest)
-   **Location:** Test files should be co-located with the component they are testing or placed in the `frontend/Test/` directory, ending with `*.test.tsx`.
-   **Principle:** Prioritize testing user behavior and interactions over implementation details.
-   **Goal:** A target Code Coverage of **70%** for core components.

## 6. Automation & Tooling

To ensure these standards are followed, we will integrate the following tools into our development and CI workflows:

-   **Backend:** The **Maven Checkstyle Plugin** will be configured to validate code against the Google Java Style Guide.
-   **Frontend:** **ESLint** and **Prettier** will be configured to automatically format code and catch stylistic or syntactical errors.