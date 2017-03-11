package com.cedarsoftware.util

import com.cedarsoftware.ncube.ApplicationID
import com.cedarsoftware.ncube.NCube
import com.cedarsoftware.ncube.NCubeManager
import com.cedarsoftware.ncube.ReleaseStatus
import com.cedarsoftware.ncube.RuleInfo
import com.cedarsoftware.ncube.exception.CoordinateNotFoundException
import com.cedarsoftware.ncube.exception.InvalidCoordinateException
import com.cedarsoftware.ncube.util.VersionComparator
import com.google.common.base.Splitter
import static com.cedarsoftware.util.RpmVisualizerConstants.*
import groovy.transform.CompileStatic

/**
 * Provides information to visualize a source rpm class, a target rpm class
 * and their relationship.
 */

@CompileStatic
class RpmVisualizerRelInfo extends VisualizerRelInfo
{
	protected RpmVisualizerHelper helper = new RpmVisualizerHelper()
	protected String sourceFieldRpmType
	protected Map<String, Map<String, Object>> sourceTraits
	protected Map<String, Map<String, Object>> targetTraits

	protected RpmVisualizerRelInfo(){}

	protected RpmVisualizerRelInfo(ApplicationID appId)
	{
		super(appId)
	}

	@Override
	protected Set<String> getRequiredScope()
	{
		Set<String> requiredScope = super.requiredScope
		requiredScope.remove(AXIS_FIELD)
		requiredScope.remove(AXIS_NAME)
		requiredScope.remove(AXIS_TRAIT)
		return requiredScope
	}

	@Override
	protected String getDetails(VisualizerInfo visInfo)
	{
		StringBuilder sb = new StringBuilder()

		//Scope messages
		sb.append(createNodeDetailsScopeMessage())

		//Scope
		if (cubeLoaded)
		{
			String title = showCellValues ? 'Utilized scope with traits' : 'Utilized scope with no traits'
			getDetailsMap(sb, title, targetScope)
		}
		getDetailsMap(sb, 'Available scope', availableTargetScope)

		//Fields
		if (cubeLoaded)
		{
			if (showCellValues)
			{
				addClassTraits(sb)
			}
			addFieldDetails(sb)
		}
		return sb.toString()
	}

	private void addFieldDetails(StringBuilder sb)
	{
		if (showCellValues)
		{
			sb.append("<b>Fields and traits</b>")
		}
		else
		{
			sb.append("<b>Fields</b>")
		}
		sb.append("<pre><ul>")
		targetTraits.each { String fieldName, v ->
			if (CLASS_TRAITS != fieldName)
			{
				if (showCellValues)
				{
					sb.append("<li><b>${fieldName}</b></li>")
					addTraits(sb, fieldName)
				}
				else
				{
					sb.append("<li>${fieldName}</li>")
				}
			}
		}
		sb.append("</ul></pre>")
	}

	private void addTraits(StringBuilder sb, String fieldName)
	{
		Map<String, Object> traits = targetTraits[fieldName].sort() as Map
		sb.append("<pre><ul>")
		traits.each { String traitName, Object traitValue ->
			if (traitValue != null)
			{
				String traitString = traitValue.toString()
				if (traitString.startsWith(HTTP) || traitString.startsWith(HTTPS) || traitString.startsWith(FILE))
				{
					sb.append("""<li>${traitName}: <a href="#" onclick='window.open("${traitString}");return false;'>${traitString}</a></li>""")
				}
				else
				{
					sb.append("<li>${traitName}: ${traitValue}</li>")
				}
			}
		}
		sb.append("</ul></pre>")
	}

	private void addClassTraits(StringBuilder sb)
	{
		sb.append("<b>Class traits</b>")
		addTraits(sb, CLASS_TRAITS)
		sb.append("${BREAK}")
	}

	@Override
	protected String getGroupName(VisualizerInfo visInfo, String cubeName = targetCube.name)
	{
		Iterable<String> splits = Splitter.on('.').split(cubeName)
		String group = splits[2].toUpperCase()
		return visInfo.allGroupsKeys.contains(group) ? group : UNSPECIFIED
	}

	private String getTargetScopedName()
	{
		Map<String, Object> classTraitsTraitMap = targetTraits ? targetTraits[CLASS_TRAITS] as Map : null
		return classTraitsTraitMap ? classTraitsTraitMap[R_SCOPED_NAME] : null
	}

	protected String getNextTargetCubeName(String targetFieldName)
	{
		if (sourceCube.getAxis(AXIS_TRAIT).findColumn(R_SCOPED_NAME))
		{
			String scopedName = sourceTraits[CLASS_TRAITS][R_SCOPED_NAME]
			return !scopedName ?: RPM_CLASS_DOT + sourceFieldRpmType
		}
		return RPM_CLASS_DOT + targetFieldName
	}

	private void retainUsedScope(VisualizerInfo visInfo, Map output)
	{
		Set<String> scopeCollector = new CaseInsensitiveSet<>()
		scopeCollector.addAll(visInfo.requiredScopeKeysByCube[targetCube.name])
		scopeCollector << EFFECTIVE_VERSION


		Set keysUsed = NCube.getRuleInfo(output).getInputKeysUsed()
		scopeCollector.addAll(keysUsed)

		Map<String, Object> scope = new CaseInsensitiveMap(availableTargetScope)
		cullScope(scope.keySet(), scopeCollector)
		targetScope.putAll(scope)
	}

	private void removeNotExistsFields()
	{
		targetTraits.keySet().removeAll { String fieldName -> !targetTraits[fieldName][R_EXISTS] }
	}

	private static void cullScope(Set<String> scopeKeys, Set scopeCollector)
	{
		scopeKeys.removeAll { String scopeKey -> !(scopeCollector.contains(scopeKey) || scopeKey.startsWith(SYSTEM_SCOPE_KEY_PREFIX)) }
	}

	@Override
	protected Map<String, Object> createEdge(int edgeCount)
	{
		Map<String, Object> edge = super.createEdge(edgeCount)
		Map<String, Map<String, Object>> sourceTraits = sourceTraits

		Map<String, Map<String, Object>> sourceFieldTraitMap = sourceTraits[sourceFieldName] as Map
		String vMin = sourceFieldTraitMap[V_MIN] as String ?: V_MIN_CARDINALITY
		String vMax = sourceFieldTraitMap[V_MAX] as String ?: V_MAX_CARDINALITY

		if (targetCube.name.startsWith(RPM_ENUM_DOT))
		{
			edge.label = nodeLabelPrefix + sourceFieldName
			edge.title = "Field ${sourceFieldName} cardinality ${vMin}:${vMax}".toString()
		}
		else
		{
			edge.title = "Valid value ${sourceFieldName} cardinality ${vMin}:${vMax}".toString()
		}

		return edge
	}

	@Override
	protected Map<String, Object> createNode(VisualizerInfo visInfo, String group = null)
	{
		Map<String, Object> node = super.createNode(visInfo, group)
		if (targetCube.name.startsWith(RPM_ENUM_DOT))
		{
			node.label = null
			node.detailsTitle2 = null
			node.title = node.detailsTitle1
			node.typesToAdd = null
		}
		return node
	}

	@Override
	protected boolean includeUnboundScopeKey(VisualizerInfo visInfo, String scopeKey)
	{
		//For the starting cube of the graph (top node) keep all unbound axis keys. For all other
		//classes (which all have a sourceCube), remove any keys that are "derived" scope keys,
		//i.e. keys that the visualizer adds to the scope as it processes through the graph
		//(keys like product, risk, coverage, sourceProduct, sourceRisk, sourceCoverage, etc.).
		if (sourceCube)
		{
			String strippedKey = scopeKey.replaceFirst('source', '')
			if (visInfo.allGroupsKeys.contains(strippedKey))
			{
				return false
			}
		}
		return true
	}

	@Override
	protected String getLabel(String cubeName)
	{
		String scopeKey = getDotSuffix(cubeName)
		return availableTargetScope[scopeKey] ?: getCubeDisplayName(cubeName)
	}

	@Override
	protected String getCubeDisplayName(String cubeName)
	{
		if (cubeName.startsWith(RPM_CLASS_DOT))
		{
			return cubeName - RPM_CLASS_DOT
		}
		else if (cubeName.startsWith(RPM_ENUM_DOT))
		{
			return cubeName - RPM_ENUM_DOT
		}
		else{
			return cubeName
		}
	}

	@Override
	protected String getSourceDescription()
	{
		String sourceCubeName = sourceCube.name
		if (sourceCubeName.startsWith(RPM_CLASS_DOT))
		{
			return getDotSuffix(getLabel(sourceCubeName))
		}
		else if (sourceCubeName.startsWith(RPM_ENUM_DOT))
		{
			if (targetScopedName)
			{
				String sourceDisplayName = getCubeDisplayName(sourceCubeName)
				String scopeKeyForSourceOfSource = getDotPrefix(sourceDisplayName)
				String nameOfSourceOfSource = sourceScope[scopeKeyForSourceOfSource]
				String fieldNameSourceOfSource = sourceScope[SOURCE_FIELD_NAME]
				return "field ${fieldNameSourceOfSource} on ${nameOfSourceOfSource}".toString()
			}
			else{
				return getCubeDisplayName(sourceCubeName)
			}
		}
		return null
	}

	@Override
	protected String getCubeDetailsTitle1()
	{
		String targetCubeName = targetCube.name
		if (targetCubeName.startsWith(RPM_CLASS_DOT))
		{
			return getCubeDisplayName(targetCubeName)
		}
		else if (targetCubeName.startsWith(RPM_ENUM_DOT))
		{
			String prefix = nodeLabelPrefix ? "${nodeLabelPrefix}valid" : 'Valid'
			return "${prefix} values for field ${sourceFieldName} on ${getLabel(sourceCube.name)}".toString()
		}
		return null
	}

	@Override
	protected String getCubeDetailsTitle2(String label)
	{
		return targetCube.name.startsWith(RPM_CLASS_DOT) ? label : null
	}

	/**
	 * Loads fields and traits on the class into the targetTraits map.
	 * If the current node is the selected node and an invalid, missing or unbound scope key is encountered,
	 * checks if the inputScope contains the key. If yes, loads again using the key provided in inputScope.
	 *
	 * @return boolean cubeLoaded
	 */

	protected boolean loadCube(VisualizerInfo visInfo, Map output = new CaseInsensitiveMap())
	{
		loadAgain = false
		try
		{
			targetTraits = new CaseInsensitiveMap()
			if (targetCube.name.startsWith(RPM_ENUM))
			{
				helper.loadRpmClassFields(appId, RPM_ENUM, targetCube.name - RPM_ENUM_DOT, availableTargetScope, targetTraits, showCellValues, output)
			}
			else
			{
				helper.loadRpmClassFields(appId, RPM_CLASS, targetCube.name - RPM_CLASS_DOT, availableTargetScope, targetTraits, showCellValues, output)
			}
			handleUnboundScope(visInfo, targetCube.getRuleInfo(output))
			removeNotExistsFields()
			cubeLoaded = true
			showCellValuesLink = true
		}
		catch (Exception e)
		{
			cubeLoaded = false
			showCellValuesLink = false
			Throwable t = helper.getDeepestException(e)
			if (t instanceof InvalidCoordinateException)
			{
				handleInvalidCoordinateException(t as InvalidCoordinateException, visInfo)
			}
			else if (t instanceof CoordinateNotFoundException)
			{
				handleCoordinateNotFoundException(t as CoordinateNotFoundException, visInfo)
			}
			else
			{
				handleException(t, visInfo)
			}
		}
		addRequiredScopeKeys(visInfo)
		retainUsedScope(visInfo, output)
		if (loadAgain)
		{
			return loadCube(visInfo, output)
		}
		return true
	}

	private void handleUnboundScope(VisualizerInfo visInfo, RuleInfo ruleInfo)
	{
		List<MapEntry> unboundAxesList = ruleInfo.getUnboundAxesList()
		if (unboundAxesList){
			helper.handleUnboundScope(visInfo, this, unboundAxesList)
			if (loadAgain)
			{
				return
			}
			nodeDetailsMessages << "Defaults were used for some scope keys. Different values may be provided.${DOUBLE_BREAK}".toString()
		}
	}

	private void handleCoordinateNotFoundException(CoordinateNotFoundException e, VisualizerInfo visInfo)
	{
		StringBuilder sb = helper.handleCoordinateNotFoundException(e, visInfo, this)
		if (loadAgain)
		{
			return
		}
		StringBuilder message = new StringBuilder("<b>Unable to load ${loadTarget}. The value ${e.value} is not valid for ${e.axisName}.</b>${DOUBLE_BREAK}")
		message.append(sb)
		nodeDetailsMessages << message.toString()
		nodeLabelPrefix = 'Required scope value not found for '
		targetTraits = new CaseInsensitiveMap()
	}

	private void handleInvalidCoordinateException(InvalidCoordinateException e, VisualizerInfo visInfo)
	{
		StringBuilder sb = helper.handleInvalidCoordinateException(e, visInfo, this, MANDATORY_SCOPE_KEYS)
		if (loadAgain)
		{
			return
		}

		StringBuilder message = new StringBuilder("<b>Unable to load ${loadTarget}. Additional scope is required.</b> ${DOUBLE_BREAK}")
		message.append(sb)
		nodeDetailsMessages << message.toString()
		nodeLabelPrefix = 'Additional scope required for '
		targetTraits = new CaseInsensitiveMap()
	}

	private void handleException(Throwable e, VisualizerInfo visInfo)
	{
		StringBuilder sb = new StringBuilder("<b>Unable to load ${loadTarget} due to an exception.</b>${DOUBLE_BREAK}")
		sb.append(helper.handleException(e))
		nodeDetailsMessages << sb.toString()
		nodeLabelPrefix = "Unable to load "
		targetTraits = new CaseInsensitiveMap()
	}

	/**
	 * Sets the basic nodeScope required to load a target class based on scoped source class,
	 * source field name, target class name, and current nodeScope.
	 * Retains all other nodeScope.
	 *
	 * @param targetCube String target cube
	 * @param sourceFieldRpmType String source field type
	 * @param sourceFieldName String source field name
	 * @param scope Map<String, Object> nodeScope
	 *
	 * @return Map new nodeScope
	 *
	 */
	protected void populateScopeRelativeToSource(String sourceFieldRpmType, String targetFieldName, Map scope)
	{
		availableTargetScope = new CaseInsensitiveMap(scope)

		if (targetCube.name.startsWith(RPM_ENUM))
		{
			availableTargetScope[SOURCE_FIELD_NAME] = targetFieldName
		}
		else if (targetCube.getAxis(AXIS_TRAIT).findColumn(R_SCOPED_NAME))
		{
			String newScopeKey = sourceFieldRpmType
			String oldValue = scope[newScopeKey]
			if (oldValue)
			{
				availableTargetScope[SOURCE_SCOPE_KEY_PREFIX + sourceFieldRpmType] = oldValue
			}
			availableTargetScope[newScopeKey] = targetFieldName
		}
	}

	@Override
	protected void populateScopeDefaults(VisualizerInfo visInfo)
	{
		Map<String, Object> scopeDefaults = new CaseInsensitiveMap()
		String date = DATE_TIME_FORMAT.format(new Date())

		String scopeValue = visInfo.inputScope[POLICY_CONTROL_DATE] ?: date
		addScopeDefault(scopeDefaults, POLICY_CONTROL_DATE, scopeValue)

		scopeValue = visInfo.inputScope[QUOTE_DATE] ?: date
		addScopeDefault(scopeDefaults, QUOTE_DATE, scopeValue)

		scopeValue = visInfo.inputScope[EFFECTIVE_VERSION] ?: appId.version
		addScopeDefault(scopeDefaults, EFFECTIVE_VERSION, scopeValue)
		loadAvailableScopeValuesEffectiveVersion(visInfo)

		availableTargetScope.putAll(scopeDefaults)
	}

	private void addScopeDefault(Map<String, Object> scopeDefaults, String scopeKey, Object value)
	{
		addNodeScope(null, scopeKey, true, null)
		scopeDefaults[scopeKey] = value
	}

	private void loadAvailableScopeValuesEffectiveVersion(VisualizerInfo visInfo)
	{
		RpmVisualizerInfo rpmVisInfo = visInfo as RpmVisualizerInfo
		if (!rpmVisInfo.effectiveVersionAvailableValues)
		{
			Map<String, List<String>> versionsMap = NCubeManager.getVersions(appId.tenant, appId.app)
			Set<Object> values = new TreeSet<>(new VersionComparator())
			values.addAll(versionsMap[ReleaseStatus.RELEASE.name()])
			values.addAll(versionsMap[ReleaseStatus.SNAPSHOT.name()])
			rpmVisInfo.effectiveVersionAvailableValues = new LinkedHashSet(values)
		}
		availableScopeValues[EFFECTIVE_VERSION] = rpmVisInfo.effectiveVersionAvailableValues
	}

	@Override
	protected void setLoadAgain(VisualizerInfo visInfo, String scopeKey)
	{
		Object scopeValue = visInfo.inputScope[scopeKey]
		if (availableTargetScope[scopeKey] == scopeValue)
		{
			loadAgain = false
		}
		else
		{
			availableTargetScope[scopeKey] = scopeValue
			targetScope[scopeKey] = scopeValue
			loadAgain = true
		}
	}

	/* TODO: Will revisit providing "in scope" available scope values for r:exists at a later time.
    @Override
    protected Set<Object> getColumnValues(String cubeName, String axisName, Map coordinate)
    {
        NCube cube = NCubeManager.getCube(appId, cubeName)
        if (coordinate && R_EXISTS == coordinate[AXIS_TRAIT])
        {
            try
            {
                return getInScopeColumnValues(cube, axisName, coordinate)
            }
            catch (CoordinateNotFoundException|InvalidCoordinateException e)
            {
                //There is more than one missing or invalid scope key so cannot determine "in scope" column values.
                //Get all column values instead.
                int debug = 0
            }
        }
        return getAllColumnValues(cube, axisName)
    }

    private static Set<Object> getInScopeColumnValues(NCube cube, String axisName, Map coordinate)
    {
        coordinate[axisName] = new LinkedHashSet()
        Map map = cube.getMap(coordinate)
        Map inScopeMapEntries = map.findAll{Object columnName, Object columnValue ->
            true == columnValue
        }
        return inScopeMapEntries.keySet()
    }*/

	@Override
	protected String getNodesLabel()
	{
		return 'classes'
	}

	@Override
	protected String getNodeLabel()
	{
		return 'class'
	}

	protected String getCellValuesLabel()
	{
		return 'traits'
	}

}