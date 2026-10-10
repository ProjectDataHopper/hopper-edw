"""
Unit tests for DataHopper MCP server logic and safety guardrails.
"""

import sys
import os
import unittest
from pathlib import Path

# Add server to path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "server"))
from db import kb
import main

class TestDataHopperMCP(unittest.TestCase):

    def test_coding_rules(self):
        rules = main.get_coding_rules()
        self.assertTrue(len(rules) >= 5, "Expected at least 5 core coding rules")
        rule_ids = [r["id"] for r in rules]
        self.assertIn("RULE_VFS", rule_ids)
        self.assertIn("RULE_GUI_GROUPS", rule_ids)
        self.assertIn("RULE_LOMBOK", rule_ids)
        self.assertIn("RULE_I18N_QUOTES", rule_ids)
        self.assertIn("RULE_JAVA_PIN", rule_ids)

    def test_coding_rules_filter(self):
        rules = main.get_coding_rules(topic="VFS")
        self.assertTrue(any("VFS" in r["title"] for r in rules))

    def test_model_schema_hdv(self):
        schema = main.get_model_schema(".hdv")
        self.assertEqual(schema["extension"], ".hdv")
        self.assertEqual(schema["root_xml"], "data-vault-model")
        self.assertTrue(len(schema["anti_patterns"]) > 0)

    def test_model_schema_unknown(self):
        schema = main.get_model_schema(".unknown")
        self.assertIn("error", schema)

    def test_search_knowledge_base(self):
        results = main.search_knowledge_base("catalog")
        self.assertTrue(len(results) > 0, "Expected search results for 'catalog'")

    def test_lookup_plugin(self):
        results = main.lookup_plugin_component("DataVault")
        self.assertTrue(len(results) > 0, "Expected matches for 'DataVault'")

    def test_cypher_guardrail_rejection(self):
        mutations = [
            "CREATE (n:Person {name: 'Alice'})",
            "MATCH (n) DELETE n",
            "MATCH (n) DETACH DELETE n",
            "MERGE (n:Test)",
            "DROP INDEX idx",
            "CALL dbms.security.listUsers()"
        ]
        for query in mutations:
            with self.subTest(query=query):
                with self.assertRaises(ValueError):
                    kb.query_cypher(query)

if __name__ == "__main__":
    unittest.main()
