#!/usr/bin/env python3
"""Boundary checks of ledger calculations; actual database/MCP evidence is separate."""
from pathlib import Path
import copy
import runpy
import unittest

m = runpy.run_path(str(Path(__file__).resolve().parents[1]/'deploy/acceptance/observability_ledger.py'))


class LedgerTests(unittest.TestCase):
    def window(self, start=1000, end=1900, service='a'):
        return dict(projectId='p',environment='prod',serviceId=service,startEpoch=start,endEpoch=end)

    def summary(self, count=900, errors=0, p95=.5, window=None):
        return m['window_summary'](dict(sampleCount=count,errorCount=errors,p95Seconds=p95),window or self.window())

    def test_complete_counts_not_truncated_display_rows(self):
        x = self.summary(1800,18)
        self.assertEqual(2,x['qps'])
        self.assertEqual(.01,x['errorRate'])
        self.assertTrue(x['complete'])

    def test_relative_change_retains_same_identity_and_windows(self):
        b = self.summary()
        a = self.summary(900,9,.6,self.window(1905,2805))
        c = m['compare_windows'](b,a)
        self.assertAlmostEqual(.2,c['p95RelativeIncrease'])
        self.assertEqual(.01,c['errorRateIncrease'])
        self.assertFalse(c['causalAttribution'])
        self.assertTrue(c['complete'])

    def test_empty_or_zero_denominator_has_no_confirmed_comparison(self):
        for b in [self.summary(0,0,None),self.summary(900,0,0)]:
            c = m['compare_windows'](b,self.summary(window=self.window(1900,2800)))
            self.assertFalse(c['complete'])
            self.assertIsNone(c['p95RelativeIncrease'])

    def test_foreign_and_overlapping_windows_rejected(self):
        for a in [self.summary(window=self.window(1900,2800,'other')),
                  self.summary(window=self.window(1899,2799))]:
            with self.assertRaises(ValueError): m['compare_windows'](self.summary(),a)

    def test_invalid_ledger_counts_and_duration_rejected(self):
        for count,errors,p95 in [(True,0,.5),(1,2,.5),(-1,0,.5),(1,0,float('nan')),(1,0,-1),(1,0,None)]:
            with self.assertRaises(ValueError): self.summary(count,errors,p95)

    def test_query_preserves_half_open_window_and_exact_count(self):
        sql = m['ledger_summary_sql']([m['ledger_query']('p','s',self.window(),'after')])
        self.assertIn('observed_at >=',sql)
        self.assertIn('observed_at <',sql)
        self.assertIn('COUNT(r.event_id)',sql)
        self.assertIn('CEIL(r.sample_total*0.95)',sql)
        self.assertIn('sr.sql_rank<=12',sql)


if __name__ == '__main__': unittest.main()
