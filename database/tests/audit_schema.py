#!/usr/bin/env python3
"""Compare the live public schema to the frozen dictionary and ERD inventory."""
import json
import os
import re
import subprocess
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[2]
dictionary = (root / 'database_design/data_dictionary.md').read_text(encoding='utf-8')
erd = (root / 'database_design/erd.md').read_text(encoding='utf-8')
expected_tables = {}
for section in re.split(r'^### ', dictionary, flags=re.MULTILINE)[1:]:
    name = section.splitlines()[0].strip()
    columns = {}
    for column, kind, nullable in re.findall(r'^\| ([a-z_]+) \| ([a-z]+) \| ([NY]) \|', section, flags=re.MULTILINE):
        columns[column] = (kind, nullable)
    expected_tables[name] = columns
expected_fks = set(re.findall(r'^\| ([a-z_]+) → ([a-z_]+) \| ([a-z_]+) \|', erd, flags=re.MULTILINE))

def query(sql):
    proc = subprocess.run(
        ['psql', '-X', '-At', '-F', '\t', '-d', os.environ.get('DB_NAME', 'medical_maintenance_db'), '-c', sql],
        text=True, capture_output=True, check=True, env=os.environ,
    )
    return [tuple(line.split('\t')) for line in proc.stdout.splitlines() if line]

actual_table_names = {row[0] for row in query("SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'")}
column_rows = query("SELECT table_name,column_name,data_type,is_nullable FROM information_schema.columns WHERE table_schema='public' ORDER BY table_name,ordinal_position")
actual_columns = {(table, column): (dtype, nullable) for table, column, dtype, nullable in column_rows}
fk_rows = query("""SELECT parent.relname,child.relname,attr.attname
FROM pg_constraint con
JOIN pg_class child ON child.oid=con.conrelid
JOIN pg_class parent ON parent.oid=con.confrelid
JOIN pg_namespace ns ON ns.oid=child.relnamespace
JOIN pg_attribute attr ON attr.attrelid=child.oid AND attr.attnum=con.conkey[1]
WHERE ns.nspname='public' AND con.contype='f' AND array_length(con.conkey,1)=1""")
actual_fks = set(fk_rows)
kind_types = {
    'identifier': 'bigint', 'reference': 'bigint', 'integer': 'integer',
    'boolean': 'boolean', 'date': 'date', 'datetime': 'timestamp with time zone',
    'string': 'text', 'text': 'text', 'enum': 'text',
}
errors = []
if actual_table_names != set(expected_tables):
    errors.append({'table_missing': sorted(set(expected_tables)-actual_table_names), 'table_extra': sorted(actual_table_names-set(expected_tables))})
expected_column_keys = {(table, col) for table, cols in expected_tables.items() for col in cols}
if set(actual_columns) != expected_column_keys:
    errors.append({'column_missing': sorted(expected_column_keys-set(actual_columns)), 'column_extra': sorted(set(actual_columns)-expected_column_keys)})
for table, columns in expected_tables.items():
    for col, (kind, nullable) in columns.items():
        actual = actual_columns.get((table, col))
        target = (kind_types[kind], 'NO' if nullable == 'N' else 'YES')
        if actual is not None and actual != target:
            errors.append({'column': f'{table}.{col}', 'expected': target, 'actual': actual})
if actual_fks != expected_fks:
    errors.append({'fk_missing': sorted(expected_fks-actual_fks), 'fk_extra': sorted(actual_fks-expected_fks)})
expected_states = {}
for key, label in [('plan', 'Plan'), ('item', 'Item')]:
    line = re.search(r'^- ' + label + r': (.*)$', dictionary, flags=re.MULTILINE).group(1)
    expected_states[key] = set(re.findall(r'`([^`]+)`', line))
actual_states = {}
for key, constraint_name in [('plan', 'ck_plan_status'), ('item', 'ck_item_status')]:
    definition = query("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname='" + constraint_name + "'")[0][0]
    actual_states[key] = set(re.findall(r"'([^']+)'::text", definition))
    if actual_states[key] != expected_states[key]:
        errors.append({'state_vocabulary': key, 'missing': sorted(expected_states[key]-actual_states[key]), 'extra': sorted(actual_states[key]-expected_states[key])})
pk, unique, checks, indexes = query("""SELECT
(SELECT count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace WHERE n.nspname='public' AND c.contype='p'),
(SELECT count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace WHERE n.nspname='public' AND c.contype='u'),
(SELECT count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace WHERE n.nspname='public' AND c.contype='c'),
(SELECT count(*) FROM pg_indexes WHERE schemaname='public')""")[0]
summary = {
    'status': 'PASS' if not errors else 'FAIL',
    'database': os.environ.get('DB_NAME', 'medical_maintenance_db'),
    'tables': len(actual_table_names), 'columns': len(actual_columns), 'foreign_keys': len(actual_fks),
    'primary_keys': int(pk), 'unique_constraints': int(unique), 'check_constraints': int(checks),
    'indexes': int(indexes), 'plan_states': len(actual_states['plan']), 'item_states': len(actual_states['item']), 'total_constraints': int(pk)+int(unique)+int(checks)+len(actual_fks),
    'expected_tables': sorted(expected_tables), 'errors': errors,
}
output = root / 'reports/phase_1_2_database_schema_audit.json'
output.write_text(json.dumps(summary, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
print(f"SCHEMA AUDIT {summary['status']}: {summary['tables']} tables, {summary['columns']} columns, {summary['foreign_keys']} FKs, {summary['indexes']} indexes, {summary['total_constraints']} constraints")
if errors:
    print(json.dumps(errors, indent=2, ensure_ascii=False), file=sys.stderr)
    sys.exit(1)
