import { ApiResponse, opsAdminService } from './ops-admin-service';

export class OpsTelemetryService {
  telemetry(): Promise<ApiResponse<Record<string, any>>> {
    return opsAdminService.telemetry();
  }
}

export const opsTelemetryService = new OpsTelemetryService();
