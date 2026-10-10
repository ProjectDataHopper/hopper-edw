"""
Coding and architectural rules extractor for Data Hopper EDW & ProjectDataHopper.
Extracts rules from CLAUDE.md, repo guidelines, and Hop architectural standards.
"""

from typing import List, Dict, Any

RULES: List[Dict[str, Any]] = [
    {
        "id": "RULE_VFS",
        "title": "Apache Commons VFS over java.io.File",
        "category": "File Access",
        "severity": "CRITICAL",
        "description": (
            "Unless there really is no other choice, files must be read from or written to "
            "using Apache Commons VFS, HopVfs.getInputStream() or HopVfs.getOutputStream(). "
            "Use of java.io.File is strictly reserved for rare and exceptional cases. "
            "The default is org.apache.commons.vfs2.FileObject."
        ),
        "good_example": (
            "try (InputStream in = HopVfs.getInputStream(filename)) {\n"
            "    FileObject fileObject = HopVfs.getFileObject(filename);\n"
            "    // process stream\n"
            "}"
        ),
        "bad_example": (
            "File file = new File(filename);\n"
            "FileInputStream fis = new FileInputStream(file);"
        )
    },
    {
        "id": "RULE_GUI_GROUPS",
        "title": "Grouped GuiCompositeWidgets for Dialogs (No Flat Forms)",
        "category": "GUI & Dialogs",
        "severity": "CRITICAL",
        "description": (
            "When building or changing a transform, action, metadata, or run-configuration dialog, "
            "do not hand-layout FormAttachment rows on the shell first. Start with annotated metadata "
            "widgets and a grouped GuiCompositeWidgets container. "
            "1. Annotate Meta with @GuiPlugin and Lombok @Getter/@Setter.\n"
            "2. Define GUI_PLUGIN_ELEMENT_PARENT_ID.\n"
            "3. Annotate user-visible fields with @HopMetadataProperty and @GuiWidgetElement.\n"
            "4. Put every field in a group (groupType = GuiWidgetGroupType.BOXES or TABS).\n"
            "5. In open(): createShell(...), buildButtonBar(), then GuiCompositeWidgets.addScrolledComposite(...).\n"
            "6. On OK: widgets.getWidgetsContents(input, PARENT_ID). Do not copy fields by hand."
        ),
        "good_example": (
            "@GuiWidgetElement(\n"
            "    id = WIDGET_DATA_SET_NAME,\n"
            "    order = \"0100\",\n"
            "    type = GuiElementType.METADATA,\n"
            "    parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,\n"
            "    groupType = GuiWidgetGroupType.BOXES,\n"
            "    group = \"Data Set\")\n"
            "@HopMetadataProperty\n"
            "private String dataSetName;"
        ),
        "bad_example": (
            "// Hand-attaching flat FormAttachment rows directly beneath transform name,\n"
            "// causing OK/Cancel buttons to overlap widgets on resize."
        )
    },
    {
        "id": "RULE_GUI_BACKED",
        "title": "All Features Must Be GUI-Backed",
        "category": "Architecture",
        "severity": "HIGH",
        "description": (
            "For Apache Hop related projects or sessions involving the Apache Hop API, "
            "avoid building any functionality that is only expressed in a file and not in a GUI element. "
            "Every metadata type, modeler feature, or transform option must have an accessible GUI control."
        ),
        "good_example": "Provide an editor dialog or visual canvas action for any new configuration property.",
        "bad_example": "Adding JSON/XML properties that can only be set by manual text-editor modifications."
    },
    {
        "id": "RULE_LOMBOK",
        "title": "Mandatory Lombok Usage",
        "category": "Code Quality",
        "severity": "HIGH",
        "description": (
            "Apache Hop and related projects must use Lombok (@Getter, @Setter, @EqualsAndHashCode, etc.) "
            "for all classes to avoid cluttering code with boilerplate getter/setter methods."
        ),
        "good_example": "@Getter\n@Setter\npublic class DataCatalogMeta implements IHopMetadata { ... }",
        "bad_example": "Writing manual getX() and setX() methods for dozens of fields."
    },
    {
        "id": "RULE_I18N_QUOTES",
        "title": "Hop i18n Resource Bundle Single-Quote Escaping",
        "category": "Localization",
        "severity": "HIGH",
        "description": (
            "Values defined in resource bundles (messages/messages*.properties) must be properly "
            "escaped. Variable expressions like ${VARIABLE} must be surrounded with single quotes: "
            "'${VARIABLE}'. Otherwise, MessageFormat drops them."
        ),
        "good_example": "MyTransform.Param.Tooltip = Specify folder path, e.g. '${HOP_DATASETS_FOLDER}'",
        "bad_example": "MyTransform.Param.Tooltip = Specify folder path, e.g. ${HOP_DATASETS_FOLDER}"
    },
    {
        "id": "RULE_JAVA_PIN",
        "title": "Java 21 and Hop 2.20.0-SNAPSHOT Hard Pins",
        "category": "Build & Dependencies",
        "severity": "CRITICAL",
        "description": (
            "Strictly compiled with JDK 21. Apache Hop pin is 2.20.0-SNAPSHOT (apache/hop#8330 AI Advisor, "
            "#8346 diagram exporters, #8297 Hop Web explorer HTML document base). "
            "Dependencies hop-core, hop-engine, and hop-ui are 'provided' by the runtime."
        ),
        "good_example": "<hop.version>2.20.0-SNAPSHOT</hop.version> with JDK 21 compiler release",
        "bad_example": "Targeting Java 11 or Hop 2.11 without approval."
    }
]

def get_rules() -> List[Dict[str, Any]]:
    return RULES
