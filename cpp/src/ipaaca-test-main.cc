#include <ipaaca.h>
#include <typeinfo>

//#include <rsc/logging/Logger.h>
//#include <rsc/logging/LoggerFactory.h>
// //rsc::logging::LoggerFactory::getInstance().reconfigure(rsc::logging::Logger::LEVEL_ALL);

//
//   TESTS
//

using namespace ipaaca;

#ifdef MAKE_RECEIVER
void my_first_iu_handler(IUInterface::ptr iu, IUEventType type, bool local)
{
	std::cout << "[32m" << iu_event_type_to_str(type) << "[m" << std::endl;
}
int main() {
	try{
		initialize_ipaaca_rsb();
		
		InputBuffer::ptr ib = InputBuffer::create("Tester", "testcategory");
		ib->register_handler(my_first_iu_handler);
		
		while (true) {
			sleep(1);
		}
		
	} catch (ipaaca::Exception& e) {
		std::cout << "== IPAACA EXCEPTION == " << e.what() << std::endl;
	}
}
#else
#ifdef MAKE_SENDER
int main() {
	try{
		initialize_ipaaca_rsb();
		
		
		OutputBuffer::ptr ob = OutputBuffer::create("Tester");
		std::cout << "Buffer: " << ob->unique_name() << std::endl;
		
		IU::ref iu = IU::create("testcategory");
		ob->add(iu);
		
		std::cout << "_payload.get(\"TEST\") = \"" << iu->_payload.get("TEST") << "\"" << std::endl;
		std::cout << "_payload[\"TEST\"] = \"" << (std::string) iu->_payload["TEST"] << "\"" << std::endl;
		iu->_payload["TEST"] = "123.5-WAS-SET";
		std::cout << "_payload[\"TEST\"] = \"" << (std::string) iu->_payload["TEST"] << "\"" << std::endl;
		
		std::string s = "The string \"" + iu->_payload["TEST"].to_str() + "\" is the new value.";
		std::cout << "Concatenation test: " << s << std::endl;
		
		iu->add_link("grin", "DUMMY_IU_UID");
		
		std::cout << "Interpreted as  long  value: " << iu->_payload["TEST"].to_int() << std::endl;
		std::cout << "Interpreted as double value: " << iu->_payload["TEST"].to_float() << std::endl;
		iu->commit();
	} catch (ipaaca::Exception& e) {
		std::cout << "== IPAACA EXCEPTION == " << e.what() << std::endl;
	}
}

#endif
#endif

