package org.genemania.adminweb.web.model;


public class FunctionsFolderTN extends TreeNode {
    public static final String NODETYPE = "functions_folder_node";

    private int organismId;

    public FunctionsFolderTN(String title) {
        super(title);
        setFolder(true);
        setType(NODETYPE);
    }


    @Override
    public String getKey() {
        return String.format("o=%d:functionsFolder", getOrganismId());
    }

	public int getOrganismId() {
		return organismId;
	}

	public void setOrganismId(int organismId) {
		this.organismId = organismId;
	}
}
