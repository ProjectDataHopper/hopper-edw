"""
Java source parser for hopper-edw.
Extracts class metadata, Hop annotations (@GuiPlugin, @GuiWidgetElement, @HopMetadata, @Transform, @Action),
and widget layout configurations.
"""

from pathlib import Path
from typing import List, Dict, Any
import re

def parse_java_files(repo_root: Path) -> List[Dict[str, Any]]:
    java_root = repo_root / "src" / "main" / "java"
    classes = []
    
    if not java_root.exists():
        return classes

    for file_path in java_root.glob("**/*.java"):
        code = file_path.read_text(encoding="utf-8", errors="replace")
        rel_path = str(file_path.relative_to(repo_root))
        
        # Package extraction
        pkg_match = re.search(r"package\s+([\w\.]+);", code)
        pkg = pkg_match.group(1) if pkg_match else ""
        
        # Class / Interface name
        class_match = re.search(r"(public\s+)?(class|interface|enum)\s+(\w+)", code)
        if not class_match:
            continue
        
        kind = class_match.group(2)
        name = class_match.group(3)
        qname = f"{pkg}.{name}" if pkg else name
        
        # Check annotations
        is_gui_plugin = "@GuiPlugin" in code
        is_metadata = "@HopMetadata" in code
        is_transform = "@Transform" in code
        is_action = "@Action" in code
        is_file_type = "@HopFileTypePlugin" in code

        # Extract Plugin metadata if present
        plugin_id = None
        plugin_name = None
        id_match = re.search(r'id\s*=\s*"([^"]+)"', code)
        if id_match:
            plugin_id = id_match.group(1)
        name_match = re.search(r'name\s*=\s*"([^"]+)"', code)
        if name_match:
            plugin_name = name_match.group(1)

        # Extract GuiWidgetElements
        widget_elements = []
        widget_matches = re.finditer(
            r'@GuiWidgetElement\s*\((.*?)\)\s*(@[^\n]+\s*)*(private|protected|public)?\s+([\w\<\>]+)\s+(\w+);',
            code,
            re.DOTALL
        )
        for w in widget_matches:
            attrs = w.group(1)
            field_type = w.group(4)
            field_name = w.group(5)
            
            w_id = re.search(r'id\s*=\s*([\w\."]+)', attrs)
            w_label = re.search(r'label\s*=\s*"([^"]+)"', attrs)
            w_group = re.search(r'group\s*=\s*"([^"]+)"', attrs)
            w_grouptype = re.search(r'groupType\s*=\s*GuiWidgetGroupType\.(\w+)', attrs)
            
            widget_elements.append({
                "field": field_name,
                "type": field_type,
                "widget_id": w_id.group(1) if w_id else None,
                "label": w_label.group(1) if w_label else None,
                "group": w_group.group(1) if w_group else None,
                "group_type": w_grouptype.group(1) if w_grouptype else None,
            })

        classes.append({
            "name": name,
            "qualified_name": qname,
            "package": pkg,
            "kind": kind,
            "path": rel_path,
            "is_gui_plugin": is_gui_plugin,
            "is_metadata": is_metadata,
            "is_transform": is_transform,
            "is_action": is_action,
            "is_file_type": is_file_type,
            "plugin_id": plugin_id,
            "plugin_name": plugin_name,
            "widget_elements": widget_elements
        })

    return classes
