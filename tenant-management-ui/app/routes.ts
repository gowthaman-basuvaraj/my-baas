import { type RouteConfig, index, route } from "@react-router/dev/routes";

export default [
  index("routes/home.tsx"),
  route("applications", "routes/applications.tsx"),
  route("schemas", "routes/schemas.tsx"),
  route("settings", "routes/settings.tsx"),
  route("data", "routes/data.tsx"),
] satisfies RouteConfig;
