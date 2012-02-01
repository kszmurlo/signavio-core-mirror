package pl.net.bluesoft.rnd.processtool.editor.jpdl.object;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.aperteworkflow.editor.domain.Permission;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import pl.net.bluesoft.rnd.processtool.editor.Widget;
import pl.net.bluesoft.rnd.processtool.editor.XmlUtil;

import com.signavio.platform.exceptions.RequestException;

public class JPDLUserTask extends JPDLTask {

    private static final Logger logger = Logger.getLogger(JPDLUserTask.class);

	private Widget widget;
	private String commentary;
	private String description;
	
	private String assignee;
	private String swimlane;
	private String candidateGroups;
    private List<Permission> permissions;

    protected JPDLUserTask() {

    }

	public Widget getWidget() {
		return widget;
	}

	@Override
	public String toXML() { 
		StringBuffer sb = new StringBuffer();
		String taskConf = "";
		if (assignee != null && assignee.trim().length() > 0)
			taskConf = String.format("assignee=\"%s\"", assignee);
		else if (candidateGroups != null && candidateGroups.trim().length() > 0)
			taskConf = String.format("candidate-groups=\"%s\"", candidateGroups);
		else if (swimlane != null && swimlane.trim().length() > 0)
			taskConf = String.format("swimlane=\"%s\"", swimlane);
		
		sb.append(String.format("<task %s name=\"%s\" g=\"%d,%d,%d,%d\">\n", taskConf,name,
                boundsX, boundsY, width, height
//                x1,y1,x2-x1,y2-y1
        ));
		//sb.append(String.format("<description>Original ID: '%s'</description>\n", resourceId));
		sb.append(getTransitionsXML());
		sb.append("</task>\n");
		return sb.toString();
    }
	
	@Override
	public void fillBasicProperties(JSONObject json) throws JSONException {
		super.fillBasicProperties(json);

        // Properties from Signavio Core model attributes
        commentary = json.getJSONObject("properties").getString("documentation");
        description = json.getJSONObject("properties").getString("description");

        // Properties from Step Editor attributes
		String widgetJson = json.getJSONObject("properties").getString("aperte-conf");
		if (widgetJson != null && widgetJson.trim().length() != 0) {
            widget = new Widget();
            widgetJson = XmlUtil.replaceXmlEscapeCharacters(widgetJson);
            JSONObject widgetJsonObj = new JSONObject(widgetJson);
            assignee = widgetJsonObj.optString("assignee");
            swimlane = widgetJsonObj.optString("swimlane");
            candidateGroups = widgetJsonObj.optString("candidate_groups");
            permissions = generatePermissionsFromJSON(widgetJsonObj.optJSONArray("step-permissions"));

            checkAssignee();
            JSONArray children = widgetJsonObj.optJSONArray("children");
            JSONObject properties = widgetJsonObj.optJSONObject("properties");
            JSONArray permissions = widgetJsonObj.optJSONArray("permissions");
            widget.setWidgetId(widgetJsonObj.getString("widgetId"));
            widget.setPriority(widgetJsonObj.getInt("priority"));
            createWidgetTree(widget, children, properties, permissions);
		}
	}
	
	private void checkAssignee() {
		boolean b1 = StringUtils.isEmpty(assignee);
		boolean b2 = StringUtils.isEmpty(swimlane);
		boolean b3 = StringUtils.isEmpty(candidateGroups);
		if (b1 && b2 && b3) {
			throw new RequestException("Fill in assignee, swimlane or candidateGroups for UserTask '" + name + "'");
		}
		if ( (b1 && b2 && !b3) || (b1 && !b2 && b3) || (!b1 && b2 && b3) ) {
		} else {
			throw new RequestException("Only one of fields: assignee, swimlane, candidateGroups can be filled for UserTask '" + name + "'");
		}
	}
	
	private void createWidgetTree(Widget w, JSONArray children, JSONObject properties, JSONArray permissions) throws JSONException {
		if (properties != null) {
			Iterator it = properties.keys();
			while(it.hasNext()) {
				String key = (String)it.next();
				Object value = properties.get(key);
				w.putAttribute(key, value);
			}
		}
        List<Permission> permissionsList = generatePermissionsFromJSON(permissions);
        w.setPermissions(permissionsList);
		if (children != null) {
			for (int i = 0; i < children.length(); i++) {
				JSONObject obj = children.getJSONObject(i);
				Widget n = new Widget();
				n.setWidgetId(obj.getString("widgetId"));
                n.setPriority(obj.getInt("priority"));
				w.addChildWidget(n);
				createWidgetTree(n, obj.optJSONArray("children"), obj.optJSONObject("properties"), obj.optJSONArray("permissions"));
			}
		}
	}

    private List<Permission> generatePermissionsFromJSON(JSONArray permissions) throws JSONException {
        List<Permission> permissionsList = new ArrayList<Permission>();
        if (permissions != null) {
            for (int i = 0; i < permissions.length(); i++) {
                JSONObject obj = permissions.getJSONObject(i);
                Permission p = new Permission();
                p.setRoleName(obj.getString("roleName"));
                p.setPrivilegeName(obj.getString("privilegeName"));
                permissionsList.add(p);
        }
        }
        return permissionsList;
    }

    private String generateChildrenXML(List<Widget> list, boolean withChildrenTag) {
		if (list.isEmpty()) {
		    return "";
        }
		
		StringBuffer sb = new StringBuffer();
		if (withChildrenTag) {
		    sb.append("<children>\n");
        }
		for (Widget w : list) {
			sb.append(String.format("<config.ProcessStateWidget className=\"%s\" priority=\"%d\">\n", w.getWidgetId(), w.getPriority()));
			sb.append(generateWidgetPermissionsXML(w.getPermissions()));
			sb.append(generateAttributesXML(w.getAttributesMap()));
			sb.append(generateChildrenXML(w.getChildrenList(), true));
			sb.append("</config.ProcessStateWidget>\n");
		}
		if (withChildrenTag) {
		    sb.append("</children>\n");
        }
		return sb.toString();
	}
	
	private String generateAttributesXML(Map<String,Object> attributesMap) {
		if (attributesMap.isEmpty())
			return "";
		StringBuffer sb = new StringBuffer();
		sb.append("<attributes>\n");
		for (String key : attributesMap.keySet()) {
			Object value = attributesMap.get(key);
            String strValue = new String(Base64.decodeBase64(((String) value).getBytes()));
            if (XmlUtil.containsXmlEscapeCharacters(strValue)) {
                sb.append(String.format("<config.ProcessStateWidgetAttribute name=\"%s\"><value>%s</value></config.ProcessStateWidgetAttribute>", key, XmlUtil.wrapCDATA(strValue)));
            } else {
                sb.append(String.format("<config.ProcessStateWidgetAttribute name=\"%s\" value=\"%s\"/>", key, strValue));
            }
		}
		sb.append("</attributes>\n");
		return sb.toString();
	}
	
	private String generateWidgetPermissionsXML(List<Permission> permissions) {
        String permissionClass = "ProcessStateWidgetPermission";
        return generatePermissionsXml(permissions, permissionClass);
	}
	private String generateStatePermissionsXML(List<Permission> permissions) {
        String permissionClass = "ProcessStatePermission";
        return generatePermissionsXml(permissions, permissionClass);
	}

    private String generatePermissionsXml(List<Permission> permissions, String permissionClass) {
        if (permissions.isEmpty())
            return "";
        StringBuffer sb = new StringBuffer();
        sb.append("<permissions>\n");
        for (Permission p : permissions) {
            sb.append(String.format("<config." + permissionClass + " privilegeName=\"%s\" roleName=\"%s\"/>",
                    p.getPrivilegeName(),
                    p.getRoleName()));
        }
        sb.append("</permissions>\n");
        return sb.toString();
    }

    public String generateWidgetXML() {
		StringBuffer sb = new StringBuffer();
		sb.append(String.format("<config.ProcessStateConfiguration description=\"%s\" name=\"%s\" commentary=\"%s\">\n", description, name, commentary));
		sb.append("<widgets>\n");
		sb.append(generateWidgetPermissionsXML(widget.getPermissions()));
		sb.append(generateAttributesXML(widget.getAttributesMap()));
		sb.append(generateChildrenXML(widget.getChildrenList(), false));
		sb.append("</widgets>\n");

		if (!outgoing.isEmpty()) {
		  sb.append("<actions>\n");
 		  for (String resId : outgoing.keySet()) {
 			JPDLTransition trans = outgoing.get(resId);
 			sb.append(trans.generateStateActionXML());
 		  }
 		  sb.append("</actions>\n");
		}
        sb.append(generateStatePermissionsXML(permissions));
		sb.append("</config.ProcessStateConfiguration>\n");
		return sb.toString();
	}

	public String getSwimlane() {
		return swimlane;
	}
	
	@Override
	public String getObjectName() {
		return "User task";
	}	
}
