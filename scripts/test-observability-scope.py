#!/usr/bin/env python3
"""Unit checks for scope normalization only; not live component evidence."""
from pathlib import Path
import runpy
import copy
import unittest
m=runpy.run_path(str(Path(__file__).resolve().parents[1]/'deploy/acceptance/observability_scope.py'))

class MappingTest(unittest.TestCase):
    def setUp(self):
        self.scope={'projectId':'ops-acceptance-a','environment':'prod','serviceId':'ops-acc-a-service-2'}
        self.series=[{'metric':{'__name__':'ops04_http_requests_total','project_id':'ops-acceptance-a',
                     'service_id':'ops-acc-a-service-2','environment':'acceptance','instance':'workflow-target:8280'},
                     'values':[[1234,'10'],[1239,'15']]}]
    def test_normalizes_only_identity_and_preserves_raw_labels_and_values(self):
        original=copy.deepcopy(self.series)
        result=m['scoped_metric_series'](self.series,self.scope)
        self.assertEqual(original,self.series)
        self.assertEqual(result[0]['values'],original[0]['values'])
        self.assertEqual(result[0]['sourceMetric'],original[0]['metric'])
        self.assertEqual('prod',result[0]['metric']['environment'])
    def test_never_maps_test_or_another_service_into_production(self):
        for key,value in [('environment','test'),('service_id','ops-acc-a-service-1'),('project_id','other'),('instance','prepare-target:8280')]:
            series=copy.deepcopy(self.series);series[0]['metric'][key]=value
            with self.assertRaises(ValueError): m['scoped_metric_series'](series,self.scope)
        with self.assertRaises(ValueError): m['scope_mapping']('test','ops-acc-a-service-2')
    def test_legacy_scope_retains_original_output(self):
        self.scope['environment']='acceptance'
        self.assertEqual(self.series,m['scoped_metric_series'](self.series,self.scope))
        self.assertEqual('ops04-order-target:workflow-target:8280',m['scope_mapping']('acceptance','ops-acc-a-service-2')['resourceIdentity'])
        self.assertEqual('service://ops-acc-a-service-2/prod',m['scope_mapping']('prod','ops-acc-a-service-2')['resourceIdentity'])

if __name__=='__main__': unittest.main()
