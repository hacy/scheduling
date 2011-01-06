#include "api_scilab.h"
#include "stack-c.h"
#include "ScilabObjectsk.h"
#include "import_java.h"
#include "MALLOC.h"

int
sci_javaCast (char *fname)
{
  CheckRhs(2, 2);

  SciErr err;
  int tmpvar[2] = {0, 0};
  int *addr = NULL;
  
  err = getVarAddressFromPosition(pvApiCtx, 1, &addr);
  if (err.iErr)
    {
      printError(&err, 0);
      return 0;
    }
  
  int idObj = getIdOfArg(addr, fname, tmpvar, 0, 1);

  if (idObj == -1)
    {
      return 0;
    }

  char *objName = getSingleString(2, fname);

  if (!objName)
    {
      return 0;
    }

  char *errmsg = NULL;
  int ret = javacast(idObj, objName, &errmsg);
  FREE(objName);

  int i;
  for (i = 1; i <= *tmpvar; i++)
    {
      removescilabjavaobject(tmpvar[i]);
    }
  
  if (errmsg)
    {
      Scierror(999, "%s: An exception has been thrown by Java :\n%s\n", fname, errmsg);
      FREE(errmsg);
      return 0;
    }

  if (!createJObj(Rhs + 1, ret))
    {
      return 0;
    }

  LhsVar(1) = Rhs + 1;

  return 0;
}
