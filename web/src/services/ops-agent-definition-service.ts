import {
  ApiResponse,
  OpsAgentBindingValidationResult,
  OpsAgentCapabilitySet,
  OpsAgentCapabilityBinding,
  OpsAgentChatRequest,
  OpsAgentChatResponse,
  OpsAgentDefinition,
  opsAdminService,
} from './ops-admin-service';

export class OpsAgentDefinitionService {
  listAgents(projectId: string): Promise<ApiResponse<OpsAgentDefinition[]>> {
    return opsAdminService.listAgents(projectId);
  }

  getAgent(agentId: string): Promise<ApiResponse<OpsAgentDefinition>> {
    return opsAdminService.getAgent(agentId);
  }

  getProjectAgentCapabilities(projectId: string): Promise<ApiResponse<OpsAgentCapabilitySet>> {
    return opsAdminService.getProjectAgentCapabilities(projectId);
  }

  validateAgentBindings(agentId: string, definition: OpsAgentDefinition): Promise<ApiResponse<OpsAgentBindingValidationResult>> {
    return opsAdminService.validateAgentBindings(agentId, definition);
  }

  getAgentBindings(agentId: string): Promise<ApiResponse<OpsAgentCapabilityBinding[]>> {
    return opsAdminService.getAgentBindings(agentId);
  }

  updateAgentBindings(agentId: string, bindings: OpsAgentCapabilityBinding[]): Promise<ApiResponse<OpsAgentDefinition & { bindings?: OpsAgentCapabilityBinding[] }>> {
    return opsAdminService.updateAgentBindings(agentId, bindings);
  }

  saveAgent(definition: OpsAgentDefinition): Promise<ApiResponse<OpsAgentDefinition>> {
    return opsAdminService.saveAgent(definition);
  }

  saveAgentDraft(definition: OpsAgentDefinition): Promise<ApiResponse<OpsAgentDefinition>> {
    return opsAdminService.saveAgentDraft(definition);
  }

  deleteAgent(agentId: string): Promise<ApiResponse<boolean>> {
    return opsAdminService.deleteAgent(agentId);
  }

  listAgentVersions(agentId: string): Promise<ApiResponse<OpsAgentDefinition[]>> {
    return opsAdminService.listAgentVersions(agentId);
  }

  validateAgentVersion(agentId: string, version: number): Promise<ApiResponse<OpsAgentDefinition>> {
    return opsAdminService.validateAgentVersion(agentId, version);
  }

  publishAgentVersion(agentId: string, version: number): Promise<ApiResponse<OpsAgentDefinition>> {
    return opsAdminService.publishAgentVersion(agentId, version);
  }

  rollbackAgentVersion(agentId: string, version: number): Promise<ApiResponse<OpsAgentDefinition>> {
    return opsAdminService.rollbackAgentVersion(agentId, version);
  }

  disableAgentVersion(agentId: string, version: number): Promise<ApiResponse<boolean>> {
    return opsAdminService.disableAgentVersion(agentId, version);
  }

  async testRunAgent(request: OpsAgentChatRequest): Promise<ApiResponse<OpsAgentChatResponse>> {
    if (!request.agentDefinition) {
      return opsAdminService.testRunAgent(request);
    }

    const draftResponse = await opsAdminService.saveAgentDraft(request.agentDefinition);
    const draft = draftResponse.data;
    if (!draft?.agentId || !draft.version) {
      throw new Error('Workflow 测试前未生成可追溯的草稿版本');
    }

    const { agentDefinition: _inlineDefinition, engine: _clientEngine, ...safeRequest } = request;
    const response = await opsAdminService.testRunAgent({
      ...safeRequest,
      mode: 'WORKFLOW',
      agentDefinitionId: draft.agentId,
      agentVersion: draft.version,
      previewDraft: true,
    });
    if (response.data) {
      response.data.metadata = {
        ...(response.data.metadata || {}),
        testedDraftAgentId: draft.agentId,
        testedDraftVersion: draft.version,
        testedDraftDefinitionHash: draft.definitionHash,
      };
    }
    return response;
  }
}

export const opsAgentDefinitionService = new OpsAgentDefinitionService();
