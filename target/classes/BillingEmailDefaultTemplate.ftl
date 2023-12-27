<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
  <style type="text/css">
    a {text-decoration: none}
    #MAILSTYLE th {font-weight: normal;}
    #MAILSTYLE ul {list-style-type: disc; padding-inline-start: 40px; margin: 0px;}
    #MAILSTYLE ol {list-style-type: decimal; padding-inline-start: 40px; margin: 0px;}
  </style>
</head>
<body style="background-color:#eee;">
<table width="100%" height="80%" cellpadding="10" cellspacing="0" border="0" style="background-color:#eee;">
  <tr><td width="50%">&nbsp;</td><td align="center">
      <table id="MAILSTYLE"  cellpadding="0" cellspacing="0" border="0" style="empty-cells: show; width: 595px; border-collapse: collapse; background-color: white;">
        <tr valign="top" style="height:0">
          <td style="width:30px"></td>
          <td style="width:2px"></td>
          <td style="width:98px"></td>
          <td style="width:440px"></td>
          <td style="width:25px"></td>
        </tr>
        <tr valign="top" style="height:205px">
          <td colspan="5" align="center">
            <#if logoName??>
              <p>
                <img src="cid:${logoName}" alt="Logo"/>
              </p>
            </#if>
          </td>
        </tr>
        <tr valign="top" style="height:20px">
          <td>
          </td>
          <td colspan="4" style="text-indent: 0px; text-align: left;">
            Liebe/r ${recipientName!},
          </td>
        </tr>
        <tr valign="top" style="height:10px">
          <td colspan="5">
          </td>
        </tr>
        <tr valign="top" style="height:20px">
          <td colspan="2">
          </td>
          <td colspan="2" style="text-indent: 0px; text-align: left;">
            <p>anbei findest du deine ${billingDocumentType} für den Abrechnungszeitraum ${clearingPeriodIdentifier}.</p>
            <p>Mit lieben Gr&uuml;&szlig;en,</p>
            <p>${issuerName!}</p></td>
          <td>
          </td>
        </tr>
        <tr valign="bottom">
          <td colspan="5" align="center">
            <p style="font-size:xx-small;">
              ${footerText!}
            </p>
          </td>
        </tr>
      </table>
    </td><td width="50%">&nbsp;</td></tr>
</table>
</body>
</html>
