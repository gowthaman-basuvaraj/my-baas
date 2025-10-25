# MyBaaS Tenant Management UI

A modern React-based tenant self-management portal for MyBaaS, allowing tenants to manage their applications, schemas, data, and settings.

## Features

- 🚀 Application Management - Create, update, and manage applications
- 📋 Schema Management - Define and manage JSON schemas for your entities
- 💾 Data Management - Create, search, and manage data records
- ⚙️ Settings Management - Configure tenant-specific settings
- 🔒 TypeScript by default
- 🎉 TailwindCSS for styling
- 📖 Built with [React Router](https://reactrouter.com/)

## Getting Started

### Prerequisites

- Node.js 18+
- pnpm (recommended) or npm
- MyBaaS backend API running

### Installation

Install the dependencies:

```bash
pnpm install
```

### Configuration

Copy the `.env.example` file to `.env` and configure the API base URL:

```bash
cp .env.example .env
```

Edit `.env` and set your API base URL:

```
VITE_API_BASE_URL=http://localhost:8080
```

### Development

Start the development server with HMR:

```bash
pnpm run dev
```

Your application will be available at `http://localhost:5173`.

### Authentication

On first access, you'll be prompted to enter:
- **Auth Token**: Your tenant authentication token
- **Tenant Host**: Your tenant hostname (e.g., tenant1.mybaas.com)

These credentials are stored in `sessionStorage` and will be used for all API calls.

## Building for Production

Create a production build:

```bash
pnpm run build
```

## Application Structure

```
app/
├── components/          # Reusable UI components
│   ├── Layout.tsx      # Main layout with navigation
│   └── Login.tsx       # Login/authentication component
├── contexts/           # React contexts
│   └── AuthContext.tsx # Authentication state management
├── lib/                # Utility libraries
│   └── api.ts          # API client functions
└── routes/             # Application routes
    ├── applications.tsx # Application management
    ├── schemas.tsx     # Schema management
    ├── data.tsx        # Data management and search
    └── settings.tsx    # Tenant settings
```

## Deployment

### Docker Deployment

To build and run using Docker:

```bash
docker build -t tenant-management-ui .

# Run the container
docker run -p 3000:3000 tenant-management-ui
```

The containerized application can be deployed to any platform that supports Docker, including:

- AWS ECS
- Google Cloud Run
- Azure Container Apps
- Digital Ocean App Platform
- Fly.io
- Railway

### DIY Deployment

If you're familiar with deploying Node applications, the built-in app server is production-ready.

Make sure to deploy the output of `pnpm run build`

```
├── package.json
├── pnpm-lock.yaml
├── build/
│   ├── client/    # Static assets
│   └── server/    # Server-side code
```

## API Reference

The application interacts with the following MyBaaS API endpoints:

- **Applications**: `/api/apps`
- **Schemas**: `/api/app/{appName}/schemas`
- **Data**: `/api/app/{appName}/data/{entityName}/{version?}`
- **Settings**: `/api/settings`

See the API test files in `../api-tests/` for detailed endpoint documentation.

## Styling

This application uses [Tailwind CSS](https://tailwindcss.com/) for styling with a clean, modern design.
