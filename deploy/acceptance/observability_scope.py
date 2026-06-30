"""Explicit logical scope mapping for the one isolated production target.

The existing target exports the historical physical label `acceptance`. The deployment
MCP addresses that same target as `prod`. Values and original labels are retained;
this mapping never addresses the independent TEST container or its data sources.
"""
from copy import deepcopy


def scope_mapping(environment, service):
    if environment not in ('acceptance', 'prod'):
        raise ValueError('PROJECT_OR_ENVIRONMENT_NOT_AUTHORIZED')
    return {'mappingId': 'ops08-isolated-production-alias-v1',
            'logicalEnvironment': environment, 'sourceEnvironment': 'acceptance',
            'sourceTarget': 'workflow-target:8280', 'sourceDatabase': 'ops_acceptance_business_a',
            'serviceId': service,
            'resourceIdentity': ('service://' + service + '/prod') if environment == 'prod'
                                else 'ops04-order-target:workflow-target:8280'}


def scoped_metric_series(series, selected):
    if selected['environment'] == 'acceptance':
        return series
    scope_mapping(selected['environment'], selected['serviceId'])
    result = deepcopy(series)
    for item in result:
        labels = item.get('metric', {})
        if labels.get('__name__') == 'up':
            continue
        if (labels.get('environment') != 'acceptance'
                or labels.get('project_id') != selected['projectId']
                or labels.get('service_id') != selected['serviceId']
                or labels.get('instance') != 'workflow-target:8280'):
            raise ValueError('PHYSICAL_METRIC_SCOPE_MISMATCH')
        item['sourceMetric'] = dict(labels)
        labels['environment'] = 'prod'
    return result
