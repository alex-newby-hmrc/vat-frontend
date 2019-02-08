/*
 * Copyright 2019 HM Revenue & Customs
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

package controllers

import config.FrontendAppConfig
import connectors.models.{AccountBalance, AccountSummaryData, VatData}
import connectors.payments.{PayConnector, SpjRequestBtaVat, VatPeriod}
import controllers.actions._
import javax.inject.Inject
import play.api.mvc.{Action, AnyContent}
import services.VatService
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import PaymentStartController.{localDateOrdering, toAmountInPence}
import org.joda.time.LocalDate
import play.api.i18n.{I18nSupport, MessagesApi}
import views.html.partials.account_summary.vat.generic_error

import scala.concurrent.{ExecutionContext, Future}

object PaymentStartController {
  def toAmountInPence(amountInPounds: BigDecimal): Long = (amountInPounds * 100).toLong
  implicit val localDateOrdering: Ordering[LocalDate] = new Ordering[LocalDate] {
    def compare(x: LocalDate, y: LocalDate): Int = x compareTo y
  }
}

class PaymentStartController @Inject()(appConfig: FrontendAppConfig,
                                       payConnector: PayConnector,
                                       authenticate: AuthAction,
                                       vatService: VatService,
                                       serviceInfo: ServiceInfoAction,
                                       override val messagesApi: MessagesApi)(implicit ec: ExecutionContext) extends FrontendController with I18nSupport {

  def makeAPayment: Action[AnyContent] = (authenticate andThen serviceInfo).async {
    implicit request =>
      vatService.fetchVatModel(Some(request.request.vatDecEnrolment)).flatMap {
        case VatData(AccountSummaryData(Some(AccountBalance(Some(amount))), _, openPeriods), _) =>
          val mostRecentPeriod = openPeriods.map(_.openPeriod).max
          val spjRequestBtaVat = SpjRequestBtaVat(
            toAmountInPence(amount),
            appConfig.businessAccountHomeUrl,
            appConfig.businessAccountHomeUrl,
            VatPeriod(mostRecentPeriod.getMonthOfYear, mostRecentPeriod.getYear),
            request.request.vatDecEnrolment.vrn.vrn)
          payConnector.vatPayLink(spjRequestBtaVat).map(response => Redirect(response.nextUrl))

        case _ => Future.successful(BadRequest(generic_error(appConfig.getPortalUrl("home")(Some(request.request.vatDecEnrolment)))))
      }

  }
}
