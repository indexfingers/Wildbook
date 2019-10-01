package org.ecocean.ia.plugin;

public class WSIAM extends IAPlugin {
    private String context = null;

    public WSIAM() {
        super();
    }
    public WSIAM(String context) {
        super(context);
        this.context = context;
    }

    @Override
    public boolean isEnabled() {
        return true;  //FIXME
    }

    @Override
    public boolean init(String context) {
        this.context = context;
        IA.log("WSIAM init() called on context " + context);
        return true;
    }

    @Override
    public void startup(ServletContextEvent sce) {

        //TODO genericize this to be under .ia (with startup hooks for *any* IA plugin)
        //if we dont need identificaiton, no need to prime
        //boolean skipIdent = Util.booleanNotFalse(IA.getProperty(context, "IBEISIADisableIdentification"));
        //if (!skipIdent && !org.ecocean.StartupWildbook.skipInit(sce, "PRIMEIA")) prime();
    }

  }
