<?xml version="1.0" encoding="UTF-8"?>
<!-- $Id$ -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0" xmlns:redirect="xalan://org.apache.xalan.lib.Redirect" extension-element-prefixes="redirect">
<xsl:output method="html" indent="yes" doctype-public="--//W3C//DTD HTML 4.01 Transitional//EN"/>
<!-- INPUT PARAMETERS -->
<!-- 
Absolute pathname of the output directory. 
Set this to the directory where you output the file transformed by this stylesheet.
(This file will contain the table of contents.)  All other section pages generated by
this stylesheet will be output in the same directory as well.  Every section page
will have an '.html' extension.
-->
<xsl:param name="abs-out-dir"/>
<!--
Name of the output file transformed by this stylesheet.
We need to know this filename because this stylesheet makes links to this file in
every section page.  You should normally instruct the stylesheet processor to 
output the transformed tree into 'index.htm', and consequently set this parameter
to 'index.htm'.  If you want to use another name, keep in mind that section pages
are given an '.html' extension, so it's wise to use an '.htm' extension for the index
file in order to avoid conflicts.
-->
<xsl:param name="index-file"/>
<!-- DOCUMENT-SCOPE VARIABLES -->
<xsl:variable name="dfi" select="/document/document-formatting-info"/>
<xsl:variable name="toc" select="/document/document-body/table-of-content"/>
<xsl:variable name="dmi" select="/document/document-meta-info"/>
<xsl:variable name="nbsp">
	<xsl:text> </xsl:text>
</xsl:variable>

<!-- number format for numbering sections -->
<xsl:variable name="snf">
	<xsl:choose>
		<xsl:when test="$dfi/generate-section-numbers/@number-format">
			<xsl:value-of select="$dfi/generate-section-numbers/@number-format"/>
		</xsl:when>
		<xsl:otherwise>1</xsl:otherwise>
	</xsl:choose>
</xsl:variable>

<xsl:variable name="generate-section-numbers">
	<xsl:choose>
		<xsl:when test="$dfi/generate-section-numbers  = 'no'">no</xsl:when>
		<xsl:otherwise>yes</xsl:otherwise>
	</xsl:choose>
</xsl:variable>

<!-- TEMPLATES -->

<!-- 
*************************************************************************
Template for the document element. This template matches 
other templates from here.
*************************************************************************
-->
<xsl:template match="document">
	<html>
		<head>
			<xsl:apply-templates select="$dfi/stylesheet"/>
			<meta name="generator" content="OME"/>
			<title>
				<xsl:value-of select="$dmi/title"/>
			</title>
		</head>
		<body>
			<xsl:call-template name="cover-page"/>
			<xsl:apply-templates select="document-body/table-of-content" mode="toc"/>
			<xsl:apply-templates select="document-body"/>		
		</body>
	</html>
</xsl:template>

<!-- 
*************************************************************************
Template for the document body. 
*************************************************************************
-->
<xsl:template match="document-body">
	<xsl:apply-templates/>
</xsl:template>

<!-- 
*************************************************************************
This template is handles a section element.
*************************************************************************
-->
<xsl:template match="section">
	<xsl:variable name="section-name" select="@name"/>
	<xsl:variable name="href" select="concat('#', $section-name)"/>
	<xsl:variable name="file-name" select="concat($abs-out-dir, @name, '.html')"/>
	<redirect:write select="$file-name">
		<html>
			<head>
				<xsl:apply-templates select="$dfi/stylesheet"/>
				<meta name="generator" content="OME"/>
				<title>
					<xsl:value-of select="@label"/>
				</title>
			</head>
			<body>
				<!-- 
				All content is wrapped by this table.  We have 3 rows: one for the document
				header, one for the document body, and finally one for the document footer.	
				-->
				<table width="100%" height="90%" align="center" border="0">
					<!-- Document header. -->
					<tr>
						<td width="100%" height="20" valign="middle" align="right">
							<xsl:apply-templates select="/document/document-header"/>
							<hr color="steelblue" size="1"/>
						</td>
					</tr>
					<!-- Document body. -->
					<tr>
						<td width="100%" height="90%" valign="top">
							<!-- Navigation links. -->
							<table width="100%" height="20" border="0">
								<tr>
									<td class="navigation" valing="middle">
										<a name="{@name}"/>
										<!-- navigation -->
										<xsl:call-template name="get-section-navigation">
											<xsl:with-param name="href" select="$href"/>
										</xsl:call-template>
									</td>
								</tr>
							</table>
							<!-- Section title. -->
							<xsl:variable name="section-level">
								<xsl:call-template name="get-section-level">
									<xsl:with-param name="href" select="$href"/>
								</xsl:call-template>
							</xsl:variable>
							<div>
								<p class="section-heading-{$section-level}">
									<b>
										<xsl:if test="$generate-section-numbers = 'yes'">
											<xsl:call-template name="get-section-number">
												<xsl:with-param name="href" select="$href"/>
											</xsl:call-template>
											<xsl:value-of select="$nbsp"/>
										</xsl:if>
										<xsl:value-of select="@label"/>
									</b>
								</p>
							</div>
							<!-- Section body. -->
							<div>
								<xsl:apply-templates/>
									<xsl:call-template name="sub-section-links">
										<xsl:with-param name="link-node" select="$toc//link[@href = $href]"/>
									</xsl:call-template>
							</div>
						</td>
					</tr>
					<!-- Document footer. -->
					<tr>
						<td width="100%" height="5%" valign="middle" align="right" class="document-footer">
							<hr color="steelblue" size="1"/>
							<xsl:apply-templates select="/document/document-footer"/>
						</td>
					</tr>
				</table>
			</body>
		</html>
	</redirect:write>
</xsl:template>

<!-- 
*************************************************************************
This template handles the table-of-content tag
*************************************************************************
-->
<xsl:template match="table-of-content" mode="toc">
	<a name="table_of_content">
		<b>Table of Contents</b>
	</a>
	<xsl:choose>
		<xsl:when test="$generate-section-numbers = 'yes'">
          <xsl:apply-templates select="link" mode="section-nos"/>
		</xsl:when>
		<xsl:otherwise>
			<ul>
				<xsl:apply-templates select="link" mode="no-section-nos"/>
			</ul>
		</xsl:otherwise>
	</xsl:choose>
</xsl:template>

<!--
*************************************************************************
This template renders a toc link with section numbers
*************************************************************************
-->
<xsl:template match="link" mode="section-nos">
	<xsl:variable name="section-no">
	  <xsl:number level="multiple"/>.
	</xsl:variable>
	<xsl:variable name="link" select="."/>
	<xsl:variable name="level" select="string-length(translate($section-no, '0123456789',''))"/>
	<table cellpadding="1" cellspacing="0" border="0">
		<tr>
			<td width="{$level * 25}" align="right">
				<xsl:value-of select="concat($section-no, $nbsp)"/>
			</td>
			<td>
				<a href="{substring-after(@href, '#')}.html">
					<xsl:value-of select="$link/text()"/>
				</a>
			</td>
		</tr>
	</table>
	<xsl:apply-templates select="link" mode="section-nos"/>
</xsl:template>


<!--
*************************************************************************
This template renders a toc link without section numbers
*************************************************************************
-->
<xsl:template match="link" mode="no-section-nos">
	<li>
		<a href="{substring-after(@href, '#')}.html">
			<xsl:value-of select="text()"/>
		</a>
		<xsl:if test="link">
			<ul>
				<xsl:apply-templates select="link" mode="no-section-nos"/>
			</ul>
		</xsl:if>
	</li>
</xsl:template>

<!-- 
*************************************************************************
Dummy template to avoid the below template being applied 
in normal mode.
*************************************************************************
-->
<xsl:template match="table-of-content|page-number|page-break">
</xsl:template>

<!-- 
*************************************************************************
This template renders the document header
*************************************************************************
-->
<xsl:template match="document-header">
	<div class="document-header">
		<xsl:apply-templates/>
	</div>
</xsl:template>

<!-- 
*************************************************************************
This template renders the document footer
*************************************************************************
-->
<xsl:template match="document-footer">
	<div class="document-footer">
		<xsl:apply-templates/>
	</div>
</xsl:template>

<!-- 
*************************************************************************
This template renders the cover page
*************************************************************************
-->
<xsl:template name="cover-page">
	<!-- title -->
	<div class="document-title"><xsl:value-of select="$dmi/title"/></div>
	<br/>
	<br/>
	<!-- all attributes -->
	<div class="document-attributes">
		<xsl:for-each select="$dmi/attribute">
			<xsl:value-of select="@name"/>: <xsl:apply-templates select="."/>
			<br/>
			<br/>
		</xsl:for-each>
	</div>
	<hr size="4" noshade="true"/>
</xsl:template>

<!-- 
*************************************************************************
This template renders the attribute content.
*************************************************************************
-->
<xsl:template match="attribute">
	<xsl:apply-templates/>
</xsl:template>

<!-- 
*************************************************************************
This template renders the sub section links.
*************************************************************************
-->
<xsl:template name="sub-section-links">
	<xsl:param name="link-node"/>
	<xsl:if test="$link-node/link">
		<br/>
		<br/>
		<xsl:choose>
			<xsl:when test="$generate-section-numbers = 'yes'">
				<xsl:for-each select="$link-node/link">
					<xsl:apply-templates select="." mode="section-nos"/>
				</xsl:for-each>
			</xsl:when>
			<xsl:otherwise>
				<ul>
					<xsl:for-each select="$link-node/link">
						<xsl:apply-templates select="." mode="no-section-nos"/>
					</xsl:for-each>
				</ul>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:if>
</xsl:template>

<!-- 
*************************************************************************
This template renders the navigation links for each section.
*************************************************************************
-->
<xsl:template name="get-section-navigation">
	<xsl:param name="href"/>
	<!-- xsl:variable name="nodes" select="$toc//link[@href = $href]/../link"/ -->
	<xsl:variable name="nodes" select="$toc//link"/>
	<xsl:variable name="href-parent">
		<xsl:for-each select="$nodes">
		<xsl:if test="@href = $href and parent::link">
			<xsl:value-of select="parent::link/@href"/>
		</xsl:if>
		</xsl:for-each>
	</xsl:variable>
	<!-- toc link -->
	<xsl:value-of select="$nbsp"/>
	<a href="{$index-file}">Table of Contents</a>
	<xsl:for-each select="$nodes">
		<!-- Up link -->
		<xsl:if test="@href = $href and parent::link">
			<xsl:value-of select="$nbsp"/>| Up: <a href="{substring-after(parent::link/@href, '#')}.html">
			<xsl:if test="$generate-section-numbers = 'yes'">
				<xsl:call-template name="get-section-number">
					<xsl:with-param name="href" select="parent::link/@href"/>
				</xsl:call-template>
				<xsl:value-of select="$nbsp"/>
			</xsl:if>
			<xsl:value-of select="parent::link/text()[1]"/>
			</a>
		</xsl:if>
	</xsl:for-each>
	<xsl:for-each select="$nodes">
		<!-- previous link -->
		<xsl:if test="following-sibling::link[1][@href = $href]">
			<xsl:value-of select="$nbsp"/>| Previous: <a href="{substring-after(@href, '#')}.html">
			<xsl:if test="$generate-section-numbers = 'yes'">
				<xsl:number level="multiple"/>.<xsl:value-of select="$nbsp"/>
			</xsl:if>
			<xsl:value-of select="text()[1]"/>
			</a> 
		</xsl:if>
	</xsl:for-each>
	<xsl:for-each select="$nodes">
		<!-- next link -->
		<xsl:if test="preceding-sibling::link[1][@href=$href]">
			<xsl:value-of select="$nbsp"/>| Next: <a href="{substring-after(@href, '#')}.html">
				<xsl:if test="$generate-section-numbers = 'yes'">
					<xsl:number level="multiple"/>.<xsl:value-of select="$nbsp"/>
				</xsl:if>
				<xsl:value-of select="text()[1]"/>
			</a>
		</xsl:if>
	</xsl:for-each>	
	
	<xsl:for-each select="$nodes">
		<!-- next link -->
		<xsl:if test="preceding-sibling::link[1][@href=$href-parent]">
			<xsl:value-of select="$nbsp"/>| Down: <a href="{substring-after(@href, '#')}.html">
				<xsl:if test="$generate-section-numbers = 'yes'">
					<xsl:number level="multiple"/>.<xsl:value-of select="$nbsp"/>
				</xsl:if>
				<xsl:value-of select="text()[1]"/>
			</a>
		</xsl:if>
	</xsl:for-each>		
		<!--
		<xsl:if test="following-sibling::link[1][@href = $href]">
			<xsl:value-of select="$nbsp"/>| Previous: <a href="{substring-after(@href, '#')}.html">
				<xsl:if test="$generate-section-numbers = 'yes'">
					<xsl:number level="multiple"/>.<xsl:value-of select="$nbsp"/>
				</xsl:if>
				<xsl:value-of select="text()[1]"/>
			</a>
</xsl:if>
-->
		
		
		
		
		<!-- next link -->
		<!--
		<xsl:if test="preceding-sibling::link[1][@href=$href]">
			<xsl:value-of select="$nbsp"/>| Next: <a href="{substring-after(@href, '#')}.html">
				<xsl:if test="$generate-section-numbers = 'yes'">
					<xsl:number level="multiple"/>.<xsl:value-of select="$nbsp"/>
				</xsl:if>
				<xsl:value-of select="text()[1]"/>
			</a>
		</xsl:if>
-->
	
</xsl:template>

<!-- 
*************************************************************************
This template renders the stylesheet tag.
*************************************************************************
-->
<xsl:template match="stylesheet">
	<link href="{@url}" rel="stylesheet" type="text/css"/>
</xsl:template>

<!-- 
*************************************************************************
This template renders a link to another section
*************************************************************************
-->
<xsl:template match="section-link">
	<xsl:variable name="href">
		<xsl:choose>
			<xsl:when test="contains(@href, '#')">
				<xsl:value-of select="concat(substring-before(@href, '#'), '.html#', substring-after(@href, '#'))"/>
			</xsl:when>
			<xsl:otherwise>
				<xsl:value-of select="concat(@href, '.html')"/>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:variable>
	<a href="{$href}">
		<xsl:apply-templates/>
	</a>
</xsl:template>

<!--
*************************************************************************
This template renders a link in the body i.e. not in toc.
*************************************************************************
-->
<xsl:template match="link">
	<xsl:if test="count(ancestor::table-of-content) = 0 ">
		<a href="{substring-after(@href, '#')}.html">
			<xsl:value-of select="text()"/>
		</a>
	</xsl:if>
</xsl:template>

<!-- 
*************************************************************************
This template copies all other elements, except what is matched above as 
it is to the output tree.
*************************************************************************
-->

<xsl:template match="*|@*|comment()|text()">
	<xsl:copy>
		<xsl:apply-templates select="*|@*|comment()|text()"/>
	</xsl:copy>
</xsl:template>

<!-- SECTION FUNCTIONS -->
<!--
*************************************************************************
Template to get the section no.
*************************************************************************
-->
<xsl:template name="get-section-number">
	<xsl:param name="format">
		<xsl:value-of select="$snf"/>
	</xsl:param>
	<xsl:param name="href"/>
	<xsl:for-each select="/document/document-body/table-of-content//link[@href = $href]">
		<xsl:number level="multiple" format="{$format}"/>.</xsl:for-each>
</xsl:template>

<!--
*************************************************************************
Template to get the section level.
*************************************************************************
-->
<xsl:template name="get-section-level">
	<xsl:param name="href"/>
	<xsl:for-each select="/document/document-body/table-of-content//link[@href = $href]">
		<xsl:variable name="section-number">
			<xsl:call-template name="get-section-number">
				<xsl:with-param name="href" select="$href"/>
				<xsl:with-param name="format">1</xsl:with-param>
			</xsl:call-template>
		</xsl:variable>
		<xsl:variable name="level" select="string-length(translate($section-number, '0123456789', ''))"/>
		<xsl:value-of select="$level"/>
	</xsl:for-each>
</xsl:template>

</xsl:stylesheet>