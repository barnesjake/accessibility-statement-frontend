/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.accessibilitystatementfrontend.repos

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.accessibilitystatementfrontend.config.AppConfig
import uk.gov.hmrc.accessibilitystatementfrontend.models._
import uk.gov.hmrc.accessibilitystatementfrontend.parsers._
import cats.syntax.either._
import play.api.Logging
import play.api.i18n.Lang

trait AccessibilityStatementsRepo {
  def existsByServiceKeyAndLanguage(serviceKey: String, language: Lang): Boolean
  def findByServiceKeyAndLanguage(serviceKey: String, language: Lang): Option[(AccessibilityStatement, Lang)]
}

@Singleton
case class AccessibilityStatementsSourceRepo @Inject()(
  appConfig: AppConfig,
  statementsParser: AccessibilityStatementsParser,
  statementParser: AccessibilityStatementParser)
    extends AccessibilityStatementsRepo
    with Logging {
  import appConfig._

  type RepoKey   = (String, String)
  type RepoEntry = (RepoKey, AccessibilityStatement)

  private val accessibilityStatements: Map[RepoKey, AccessibilityStatement] = {
    logger.info(s"Starting to parse accessibility statements")
    val services: Seq[String] = getServices

    logger.info(s"Found ${services.size} accessibility statements")

    val statements: Seq[RepoEntry] = services flatMap { serviceFileName =>
      logger.info(s"Parsing accessibility statement $serviceFileName")

      val (serviceName, languageCode) = serviceFileNameToNameAndLanguage(serviceFileName)
      val statement                   = getStatement(serviceFileName)

      if (isStatementVisible(statement)) {
        Seq((serviceName, languageCode) -> statement)
      } else {
        logger.info(s"Skipping accessibility statement $serviceFileName as marked as draft")

        Seq.empty
      }
    }

    logger.info(s"Accessibility statements parsed, total number of parsed statements is: ${statements.size}")
    statements.toMap
  }

  def existsByServiceKeyAndLanguage(serviceKey: String, language: Lang): Boolean =
    accessibilityStatements.isDefinedAt((serviceKey, language.code))

  def findByServiceKeyAndLanguage(serviceKey: String, language: Lang): Option[(AccessibilityStatement, Lang)] =
    accessibilityStatements.get((serviceKey, language.code)).map((_, language))

  private def serviceFileNameToNameAndLanguage(serviceFileName: String): (String, String) = {
    val welshLanguageSuffix = s".$cy"

    if (serviceFileName.endsWith(welshLanguageSuffix)) {
      (serviceFileName.dropRight(welshLanguageSuffix.length), cy)
    } else {
      (serviceFileName, en)
    }
  }

  private def getServices: Seq[String] = statementsParser.parseFromSource(statementsSource()).valueOr(throw _).services

  private def getStatement(serviceFileName: String) =
    statementParser
      .parseFromSource(statementSource(serviceFileName))
      .valueOr(throw _)

  private def isStatementVisible(statement: AccessibilityStatement) =
    showDraftStatementsEnabled || statement.statementVisibility == Public
}