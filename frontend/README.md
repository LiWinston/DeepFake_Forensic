# DeepFake Forensic Tool - Frontend

A modern React TypeScript application for detecting and analyzing deepfake, digitally altered, and synthetic media.

## Technology Stack

- **Framework**: React 19 with TypeScript
- **Build Tool**: Vite 7
- **UI Library**: Ant Design 5
- **HTTP Client**: Axios
- **Routing**: React Router DOM
- **State Management**: React Hooks

## Features

### 🔄 File Upload
- Chunked upload support for large files (up to 1GB)
- Drag & drop interface
- Real-time upload progress
- File type validation
- Duplicate file detection
- Resume interrupted uploads

### 📁 File Management
- File list with pagination and filtering
- File preview for images and videos
- Metadata display
- File operations (delete, analyze)
- Search and sort functionality

### 🔍 Metadata Analysis
- EXIF data extraction and display
- File header analysis
- Hash verification (MD5, SHA-256)
- Anomaly detection with risk scoring
- Interactive metadata tree view
- Analysis history tracking

### 📊 Dashboard
- Statistics overview
- Recent activity timeline
- System status indicators
- Quick access to key functions

## Getting Started

### Prerequisites

- Node.js 20.16+ 
- npm or yarn
- Backend API running on http://localhost:8082

### Installation

1. Install dependencies:
```bash
npm install
```

2. Start development server:
```bash
npm run dev
```

The application will be available at http://localhost:3000

## Project Structure

```
src/
├── components/           # Reusable React components
├── pages/               # Page components
├── services/            # API services
├── hooks/               # Custom React hooks
├── types/               # TypeScript type definitions
├── utils/               # Utility functions
├── constants/           # Application constants
└── App.tsx              # Main application component
```

## API Integration

The frontend communicates with the backend through REST APIs for file upload, metadata analysis, and file management.

## Development

```bash
# Start development server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview

# Type check
npm run type-check
```
